package com.example.pfe.Service;

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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RagService {

    private final ChatLanguageModel chatLanguageModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;


    public RagService(
            ChatLanguageModel chatLanguageModel,
            EmbeddingModel embeddingModel,
            @Nullable EmbeddingStore<TextSegment> embeddingStore
    ) {
        this.chatLanguageModel = chatLanguageModel;
        this.embeddingModel    = embeddingModel;
        this.embeddingStore    = embeddingStore;
    }

    // ── Ingestion PDF ────────────────────────────────────────────────────────

    public void ingestPdf(MultipartFile file) throws IOException {
        if (embeddingStore == null) {
            throw new IllegalStateException("ChromaDB is not available.");
        }
        log.info("Ingesting PDF: {}", file.getOriginalFilename());
        Document document = new ApachePdfBoxDocumentParser().parse(file.getInputStream());
        EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(500, 50))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build()
                .ingest(document);
        log.info("PDF ingested successfully: {}", file.getOriginalFilename());
    }

    // ── RAG Chat ─────────────────────────────────────────────────────────────

    public String chat(String question) {
        if (embeddingStore == null) {
            return "RAG is currently unavailable — Chroma is not running.";
        }

        log.info("HR question received: {}", question);

        // Step 1: translate query to French for ChromaDB search
        String queryForSearch = translateToFrench(question);

        // Step 2: embed and search ChromaDB
        var questionEmbedding = embeddingModel.embed(queryForSearch).content();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(questionEmbedding)
                        .maxResults(5)
                        .build()
        );

        // ── ADD THIS BLOCK right here ────────────────────────────────────────
        List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

        if (matches.isEmpty()) {
            log.warn("ChromaDB returned 0 results for query: '{}'. " +
                    "Is the PDF uploaded? Collection may be empty.", queryForSearch);
        } else {
            matches.forEach(m ->
                    log.info("ChromaDB match — score: {}, text preview: {}",
                            m.score(),
                            m.embedded().text().substring(0, Math.min(80, m.embedded().text().length())))
            );
        }
        // ── END OF ADDED BLOCK ───────────────────────────────────────────────

        String context = matches.stream()
                .map(match -> match.embedded().text())
                .collect(Collectors.joining("\n\n"));

        if (context.isEmpty()) {
            return "I don't have enough information in the HR documentation to answer this. " +
                    "Please contact the HR service for more details.";
        }

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

        String response = chatLanguageModel.generate(prompt.text());
        log.info("HR response generated successfully");
        return response;
    }

    // ── Translate query to French for embedding search ───────────────────────

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