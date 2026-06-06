package com.example.pfe.Service;

import com.example.pfe.enums.QueryCategory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueryClassifierService {

    private final ChatLanguageModel chatLanguageModel;

    private static final String NORMALIZE_PROMPT = """
            You are a spelling corrector for an HR chatbot.
            The user may write with typos, informal spelling, or mixed languages.
            Correct the spelling of the following text WITHOUT changing its meaning or language.
            If it is already correct, return it unchanged.
            Respond with ONLY the corrected text, nothing else.

            Examples:
            "congee meternitee"     → "congé maternité"
            "matenity leeve"        → "maternity leave"
            "combien de conge"      → "combien de congé"
            "salair mensuel"        → "salaire mensuel"

            Text: "%s"
            Corrected:""";

    private static final String CLASSIFIER_PROMPT = """
            You are a query classifier for an HR chatbot.
            Classify the user's question into exactly one of these categories:
            - HR_DOCUMENTATION: questions about company policies, leave (congé), attendance,
              contracts, benefits, salaries, training, internal regulations, HR procedures,
              maternity, paternity, sick leave, annual leave, holidays, work hours, etc.
            - SMALL_TALK: greetings, "hi", "hello", "how are you", "bonjour", "مرحبا",
              polite conversation, thanks, goodbye.
            - GENERAL_KNOWLEDGE: general questions unrelated to the company
              (weather, news, general facts, math, science).
            - OTHER: anything out of scope or inappropriate for this chatbot.

            When in doubt between HR_DOCUMENTATION and OTHER, choose HR_DOCUMENTATION.
            Respond ONLY with the category name, nothing else.

            User question: "%s"
            Category:""";

    public QueryCategory classify(String question) {
        // Step 1: fix typos before classifying
        String normalized = normalizeQuestion(question);
        if (!normalized.equalsIgnoreCase(question)) {
            log.info("Question normalized: '{}' → '{}'", question, normalized);
        }

        // Step 2: classify the corrected question
        String prompt = String.format(CLASSIFIER_PROMPT, normalized);
        String result = chatLanguageModel.generate(prompt).trim().toUpperCase();
        result = result.replaceAll("[^A-Z_]", "");

        try {
            QueryCategory category = QueryCategory.valueOf(result);
            log.info("Query '{}' classified as: {}", normalized, category);
            return category;
        } catch (IllegalArgumentException e) {
            log.warn("Could not parse category '{}', defaulting to HR_DOCUMENTATION", result);
            return QueryCategory.HR_DOCUMENTATION;
        }
    }

    private String normalizeQuestion(String question) {
        try {
            String prompt = String.format(NORMALIZE_PROMPT, question);
            return chatLanguageModel.generate(prompt).trim();
        } catch (Exception e) {
            log.warn("Normalization failed, using original: {}", e.getMessage());
            return question;
        }
    }
}