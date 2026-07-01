package com.example.pfe.Service;

import com.example.pfe.entities.RagIngestionLog;
import com.example.pfe.Repository.RagIngestionLogRepository;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.store.embedding.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RagService {

    private final ChatLanguageModel chatLanguageModel;// ← Ollama/Mistral pour générer du texte
    private final EmbeddingModel embeddingModel; // ← Modèle pour vectoriser le texte
    private final EmbeddingStore<TextSegment> embeddingStore;// ← ChromaDB
    private final RagIngestionLogRepository ingestionLogRepository;// ← Logs en DB
    private final FileHashService fileHashService; // ← Calcul SHA-256

    public RagService(
            ChatLanguageModel chatLanguageModel,
            EmbeddingModel embeddingModel,
            // ChromaDB peut être absent (graceful degradation).
            @Nullable EmbeddingStore<TextSegment> embeddingStore,
            RagIngestionLogRepository ingestionLogRepository,
            FileHashService fileHashService
    ) {
        this.chatLanguageModel = chatLanguageModel;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.ingestionLogRepository = ingestionLogRepository;
        this.fileHashService = fileHashService;
    }

    // ── Ingestion PDF avec slug et hash-based update ──────────────────────────
//Le coeur du système d'auto-sync intelligent.
    public void ingestPdfWithSlug(InputStream fileInputStream, String fileName, String fileSlug) throws IOException {
        if (embeddingStore == null) {
            throw new IllegalStateException("ChromaDB is not available.");
        }

        log.info("Starting ingestion: fileName={}, slug={}", fileName, fileSlug);

        // ÉTAPE 1: Lire le PDF en bytes (pour pouvoir le hasher ET le re-lire)
        ByteArrayInputStream markableStream = new ByteArrayInputStream(fileInputStream.readAllBytes());
        String newHash = fileHashService.calculateHash(markableStream);
        markableStream.reset();

        // Step 2: Vérifier si le fichier a changé
        Optional<RagIngestionLog> lastLog = ingestionLogRepository.findFirstByFileSlugOrderByIngestedAtDesc(fileSlug);
        // ÉTAPE 3: Si même hash → SKIP (économie de ressources)
        if (lastLog.isPresent() && lastLog.get().getFileHash().equals(newHash)) {
            log.info("File hash unchanged — skipping ingestion");
            // Logger un NO_CHANGE
            RagIngestionLog noChangeLog = RagIngestionLog.builder()
                    .fileSlug(fileSlug)
                    .fileName(fileName)
                    .fileHash(newHash)
                    .previousHash(lastLog.get().getFileHash())
                    .status("NO_CHANGE")
                    .message("File hash unchanged, no re-ingestion needed")
                    .ingestedAt(LocalDateTime.now())
                    .build();

            ingestionLogRepository.save(noChangeLog);
            return;
        }
// ÉTAPE 4: Sinon → INGÉRER
        try {
            // Step 3: Parser le PDF
            markableStream.reset();
            // 4a. Parser le PDF (extraire le texte)
            Document document = new ApachePdfBoxDocumentParser().parse(markableStream);

            // 4b. Découper en chunks de 500 caractères avec chevauchement de 50 caractères
            var splitter = DocumentSplitters.recursive(500, 50);
            var segments = splitter.split(document);
// 4c. Pour chaque chunk, vectoriser et stocker dans ChromaDB
            int chunksIngested = 0;
            for (TextSegment segment : segments) {
                // Ajouter des métadonnées (utile pour filtrer)
                TextSegment withMeta = TextSegment.from(
                        segment.text(),
                        dev.langchain4j.data.document.Metadata.from(Map.of(
                                "slug", fileSlug,
                                "source", fileName,
                                "hash", newHash,
                                "timestamp", LocalDateTime.now().toString()
                        ))
                );

                // Embedding (texte → vecteur 384 dimensions)
                var embedding = embeddingModel.embed(segment.text()).content();
                // Stockage dans ChromaDB

                embeddingStore.add(embedding, withMeta);
                chunksIngested++;
            }

            // 4d. Logger le SUCCESS
            RagIngestionLog successLog = RagIngestionLog.builder()
                    .fileSlug(fileSlug)
                    .fileName(fileName)
                    .fileHash(newHash)
                    .previousHash(lastLog.map(RagIngestionLog::getFileHash).orElse(null))
                    .status("SUCCESS")
                    .message("PDF ingested successfully")
                    .ingestedAt(LocalDateTime.now())
                    .chunksCount(chunksIngested)
                    .build();

            ingestionLogRepository.save(successLog);
            log.info("Ingestion completed successfully: {} chunks stored", chunksIngested);

        } catch (Exception e) {
            log.error("Ingestion failed: {}", e.getMessage(), e);
            // ÉTAPE 5: Si erreur → logger FAILED
            RagIngestionLog failureLog = RagIngestionLog.builder()
                    .fileSlug(fileSlug)
                    .fileName(fileName)
                    .fileHash(newHash)
                    .previousHash(lastLog.map(RagIngestionLog::getFileHash).orElse(null))
                    .status("FAILED")
                    .message("Error: " + e.getMessage())
                    .ingestedAt(LocalDateTime.now())
                    .build();

            ingestionLogRepository.save(failureLog);
            throw new IOException("Ingestion failed: " + e.getMessage(), e);
        }
    }

    // ── Ancien endpoint (garde pour compatibilité) ────────────────────────────

    public void ingestPdf(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream()) {
            ingestPdfWithSlug(is, file.getOriginalFilename(), "reglement_interne");
        }
    }

    // ── RAG Chat (inchangé) ──────────────────────────────────────────────────
    //Répondre à une question en utilisant le RAG.
    public String chat(String question) {
        if (embeddingStore == null) {
            return "RAG is currently unavailable — Chroma is not running.";
        }

        log.info("HR question received: {}", question);
// 1. Traduire la question en français (les documents sont en FR)
        String queryForSearch = translateToFrench(question);
        // 2. Vectoriser la question

        var questionEmbedding = embeddingModel.embed(queryForSearch).content();
        // 3. Chercher les 5 chunks les plus similaires dans ChromaDB

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(questionEmbedding)
                        .maxResults(5)
                        .build()
        );

        List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

        if (matches.isEmpty()) {
            log.warn("ChromaDB returned 0 results for query: '{}'", queryForSearch);
        } else {
            matches.forEach(m ->
                    log.info("ChromaDB match — score: {}, text preview: {}",
                            m.score(),
                            m.embedded().text().substring(0, Math.min(80, m.embedded().text().length())))
            );
        }
// 4. Concaténer les chunks trouvés pour former le contexte
        String context = matches.stream()
                .map(match -> match.embedded().text())
                .collect(Collectors.joining("\n\n"));
// 5. Si aucun contexte trouvé → réponse par défaut
        if (context.isEmpty()) {
            return "I don't have enough information in the HR documentation to answer this. " +
                    "Please contact the HR service for more details.";
        }
        // 6. Construire le prompt pour le LLM
        PromptTemplate template = PromptTemplate.from("""
            You are an HR assistant chatbot for Arabsoft.

            STRICT RULES:
            1. Answer ONLY using the HR documentation context provided below.
            2. Do NOT use any outside knowledge.
            3. If the answer is not found in the context, respond:
               "I don't have enough information in the HR documentation to answer this.
                Please contact the HR service for more details."
            4. Detect the language of the USER QUESTION and respond in that exact same language only.

            HR Documentation Context:
            {{context}}

            User question: {{question}}

            Answer:""");

        Prompt prompt = template.apply(Map.of(
                "context", context,
                "question", question
        ));
// 7. Demander au LLM de générer la réponse
        String response = chatLanguageModel.generate(prompt.text());
        log.info("HR response generated successfully");
        return response;
    }

    private String translateToFrench(String question) {
        String translationPrompt = """
            You are a translator specialized in HR (Human Resources) terminology.
            Translate the following HR-related question to French.
            IMPORTANT: Use proper HR vocabulary:
            - "leave" or "leaves" → "congé" or "congés" (NOT "feuilles")
            - "days off" → "jours de congé"
            - "maternity leave" → "congé de maternité"
            - "sick leave" → "congé maladie"
            - "annual leave" → "congé annuel"
            - "salary" → "salaire"
            - "benefits" → "avantages sociaux"
            If the text is already in French, return it unchanged.
            Respond with ONLY the translated text, nothing else.

            Text: "%s"
            French translation:""".formatted(question);

        try {
            String translated = chatLanguageModel.generate(translationPrompt).trim();
            log.info("Query translated for search: {}", translated);
            return translated;
        } catch (Exception e) {
            log.warn("Translation failed, using original: {}", e.getMessage());
            return question;
        }
    }
}