package org.idubinov.termfind.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Тонкий клиент OpenAI-совместимого API (/chat/completions).
 * Смена провайдера = смена LLM_BASE_URL / LLM_API_KEY / LLM_MODEL.
 * Vision (приём картинок) — отдельные флаги llm.vision.enabled / llm.vision.model.
 */
@Component
public class LlmClient {

    private final RestClient restClient;
    private final String model;
    private final boolean enabled;
    private final boolean visionEnabled;
    private final String visionModel;

    public LlmClient(@Value("${llm.base-url}") String baseUrl,
                     @Value("${llm.api-key}") String apiKey,
                     @Value("${llm.model}") String model,
                     @Value("${llm.vision.enabled:false}") boolean visionEnabled,
                     @Value("${llm.vision.model:deepseek-chat}") String visionModel) {
        this.model = model;
        this.enabled = apiKey != null && !apiKey.isBlank();
        this.visionEnabled = visionEnabled;
        this.visionModel = visionModel;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    /** false = ключ не задан, нейро-функции отключены. */
    public boolean isEnabled() {
        return enabled;
    }

    /** Vision требует и ключ, и явный флаг llm.vision.enabled. */
    public boolean isVisionEnabled() {
        return enabled && visionEnabled;
    }

    /** Простое завершение диалога одним сообщением пользователя. */
    public String complete(String systemPrompt, String userPrompt) {
        return send(buildRequest(model, systemPrompt, userPrompt));
    }

    /** Мультимодальный запрос: текст + картинка (base64 PNG) в стиле OpenAI content-частей. */
    public String completeWithImage(String systemPrompt, String userPrompt, byte[] imagePng) {
        if (!isVisionEnabled()) {
            throw new IllegalStateException("Vision отключен (llm.vision.enabled=false)");
        }
        String base64 = Base64.getEncoder().encodeToString(imagePng);
        List<Map<String, Object>> content = List.of(
                Map.of("type", "text", "text", userPrompt),
                Map.of("type", "image_url", "image_url", Map.of("url", "data:image/png;base64," + base64)));
        return send(buildRequest(visionModel, systemPrompt, content));
    }

    static Map<String, Object> buildRequest(String model, String system, Object userContent) {
        return Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", userContent)),
                "temperature", 0.3);
    }

    @SuppressWarnings("unchecked")
    private String send(Map<String, Object> request) {
        Map<String, Object> response = restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            throw new IllegalStateException("Пустой ответ от LLM API");
        }
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("LLM не вернул вариантов ответа");
        }
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        return (String) message.get("content");
    }
}
