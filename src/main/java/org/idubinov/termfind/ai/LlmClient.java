package org.idubinov.termfind.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Тонкий клиент OpenAI-совместимого API (/chat/completions).
 * Смена провайдера = смена LLM_BASE_URL / LLM_API_KEY / LLM_MODEL.
 */
@Component
public class LlmClient {

    private final RestClient restClient;
    private final String model;
    private final boolean enabled;

    public LlmClient(@Value("${llm.base-url}") String baseUrl,
                     @Value("${llm.api-key}") String apiKey,
                     @Value("${llm.model}") String model) {
        this.model = model;
        this.enabled = apiKey != null && !apiKey.isBlank();
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    /** false = ключ не задан, нейро-функции отключены. */
    public boolean isEnabled() {
        return enabled;
    }

    /** Простое завершение диалога одним сообщением пользователя. */
    @SuppressWarnings("unchecked")
    public String complete(String systemPrompt, String userPrompt) {
        Map<String, Object> request = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)),
                "temperature", 0.3);

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
