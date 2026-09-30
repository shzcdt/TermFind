package org.idubinov.termfind.ai;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Флаги Vision и структура мультимодального запроса (без сетевых вызовов).
 */
class LlmClientTest {

    @Test
    void visionRequiresKeyAndFlag() {
        assertTrue(new LlmClient("http://localhost:0", "key", "m", true, "v").isVisionEnabled());
        assertFalse(new LlmClient("http://localhost:0", "key", "m", false, "v").isVisionEnabled(), "нет флага");
        assertFalse(new LlmClient("http://localhost:0", "", "m", true, "v").isVisionEnabled(), "нет ключа");
        assertFalse(new LlmClient("http://localhost:0", "", "m", true, "v").isEnabled());
    }

    @Test
    void visionRequestUsesImageContentAndVisionModel() {
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47};
        Map<String, Object> request = LlmClient.buildRequest("vision-model", "system", List.of(
                Map.of("type", "text", "text", "Термин: тест"),
                Map.of("type", "image_url", "image_url", Map.of("url", "data:image/png;base64,aPNG"))));

        assertEquals("vision-model", request.get("model"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) request.get("messages");
        assertEquals(2, messages.size());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) messages.get(1).get("content");
        assertEquals("text", content.get(0).get("type"));
        assertEquals("image_url", content.get(1).get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> imageUrl = (Map<String, Object>) content.get(1).get("image_url");
        assertTrue(((String) imageUrl.get("url")).startsWith("data:image/png;base64,"));
    }
}
