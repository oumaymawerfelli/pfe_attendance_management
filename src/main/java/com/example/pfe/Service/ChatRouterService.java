package com.example.pfe.Service;

import com.example.pfe.enums.QueryCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatRouterService {

    private final QueryClassifierService classifier;
    private final RagService ragService;

    public String route(String question) {
        QueryCategory category = classifier.classify(question);
        log.info("Routing question to: {}", category);

        return switch (category) {
            case HR_DOCUMENTATION  -> ragService.chat(question);
            case SMALL_TALK        -> handleSmallTalk(question);
            case GENERAL_KNOWLEDGE -> handleGeneral(question);
            case OTHER             -> handleOther(question);
        };
    }

    // ── Detect language then reply in that language only ─────────────────────

    private String handleSmallTalk(String question) {
        String q = question.toLowerCase();

        // ── Arabic ───────────────────────────────────────────────────────────
        if (containsArabic(q)) {
            if (q.matches(".*( شكرا|شكراً|merci).*")) return "على الرحب والسعة! 😊 لا تتردد في سؤالي عن أي شيء يخص الموارد البشرية.";
            if (q.matches(".*(وداع|مع السلامة|باي).*"))  return "وداعاً! 👋 أتمنى لك يوماً سعيداً.";
            return "مرحباً! 👋 أنا مساعدك في شؤون الموارد البشرية لـ Arabsoft. كيف يمكنني مساعدتك؟";
        }

        // ── French ───────────────────────────────────────────────────────────
        if (isFrench(q)) {
            if (q.matches(".*(merci|super|parfait).*"))  return "Avec plaisir ! 😊 N'hésitez pas si vous avez d'autres questions RH.";
            if (q.matches(".*(au revoir|bonne journée|bye).*")) return "Au revoir ! 👋 Bonne journée !";
            if (q.matches(".*(ça va|comment tu vas|comment vous allez).*")) return "Très bien, merci ! 😊 Comment puis-je vous aider avec les questions RH aujourd'hui ?";
            return "Bonjour ! 👋 Je suis votre assistant RH pour Arabsoft. Comment puis-je vous aider ?";
        }

        // ── English (default) ────────────────────────────────────────────────
        if (q.matches(".*(thank|thanks|perfect|great).*")) return "You're welcome! 😊 Feel free to ask more HR questions anytime.";
        if (q.matches(".*(bye|goodbye|see you).*"))        return "Goodbye! 👋 Have a great day!";
        if (q.matches(".*(how are you|how r u).*"))        return "I'm doing well, thank you! 😊 How can I help you with HR questions today?";
        return "Hello! 👋 I'm your HR assistant for Arabsoft. How can I help you today?";
    }

    private String handleGeneral(String question) {
        if (containsArabic(question)) {
            return "أنا متخصص في أسئلة الموارد البشرية لـ Arabsoft فقط. للأسئلة العامة، يرجى استخدام محرك بحث. 🔍";
        }
        if (isFrench(question.toLowerCase())) {
            return "Je suis spécialisé dans les questions RH d'Arabsoft uniquement. Pour les questions générales, utilisez un moteur de recherche. 🔍";
        }
        return "I'm specialized in HR-related questions for Arabsoft only. For general questions, please use a search engine. 🔍";
    }

    private String handleOther(String question) {
        if (containsArabic(question)) {
            return "هذا الروبوت مخصص لأسئلة الموارد البشرية فقط. للمواضيع الأخرى، يرجى التواصل مع قسم الموارد البشرية. 📩";
        }
        if (isFrench(question.toLowerCase())) {
            return "Ce chatbot traite uniquement les questions RH. Pour d'autres sujets, contactez le service RH. 📩";
        }
        return "This chatbot handles HR-related questions only. For other topics, please contact the HR service. 📩";
    }

    // ── Language detection helpers ────────────────────────────────────────────

    private boolean containsArabic(String text) {
        return text.chars().anyMatch(c -> Character.UnicodeBlock.of(c) == Character.UnicodeBlock.ARABIC);
    }

    private boolean isFrench(String text) {
        return text.matches(".*(bonjour|bonsoir|salut|merci|oui|non|comment|ça va|congé|réponse|au revoir|s'il vous|je |vous |nous |les |des |pour |avec |dans ).*");
    }
}