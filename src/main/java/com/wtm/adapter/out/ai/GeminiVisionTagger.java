package com.wtm.adapter.out.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtm.application.collection.ImageTags;
import com.wtm.application.collection.Reference;
import com.wtm.application.port.out.LlmUnavailableException;
import com.wtm.application.port.out.VisionTaggerPort;
import com.wtm.application.report.ReviewRequest;
import com.wtm.application.report.Suggestion;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Asks Google's Gemini to catalogue a picture, the way the local vision model does, but with far more knowledge
 * of memes: it is asked to say where a meme comes from and what it means, and to say so when it does not know
 * rather than make something up. It answers in the same JSON, so everything after it is the same.
 *
 * <p>The free tier allows only so many calls a minute and a day. Calls are spaced out, and a refusal is reported as
 * the model being unavailable, which puts the picture back in line to be tried again later.
 */
@Component
@ConditionalOnProperty(name = "wtm.vision.provider", havingValue = "gemini")
class GeminiVisionTagger implements VisionTaggerPort {

    static final String SYSTEM_PROMPT = """
            你是梗圖(迷因)資料庫的編目員,熟悉台灣與全球的網路梗、反應圖與貼圖。
            只說你確定的事:不確定出處時就只描述它表達什麼,不要編造出處、人名、年份或事件。
            看完圖片後,只輸出一個 JSON 物件,不要輸出任何其他文字。""";

    static final int MAX_ANSWER_TOKENS = 1500;

    private final RestClient client;
    private final String model;
    private final int maxSide;
    private final long minIntervalNanos;
    private final VisionAnswerParser parser;
    private final Object spacing = new Object();
    private long nextCallAt;

    GeminiVisionTagger(VisionProperties properties, ObjectMapper json) {
        var gemini = properties.gemini();
        if (gemini.apiKey() == null || gemini.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "WTM_VISION_PROVIDER=gemini needs GEMINI_API_KEY (a key from Google AI Studio) to be set");
        }
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(5));
        factory.setReadTimeout(gemini.timeout());
        this.client = RestClient.builder()
                .baseUrl(gemini.baseUrl())
                .requestFactory(factory)
                .defaultHeader("x-goog-api-key", gemini.apiKey())   // a header, so it never appears in an address or a log line
                .build();
        this.model = gemini.model();
        this.maxSide = gemini.maxSide();
        this.minIntervalNanos = gemini.minInterval().toNanos();
        this.parser = new VisionAnswerParser(json);
    }

    @Override
    public ImageTags describe(byte[] image, String contentType, String hint, Reference reference) {
        return parser.parse(ask(image, userPrompt(hint, reference), describeSchema()));
    }

    @Override
    public Suggestion reassess(byte[] image, String contentType, ReviewRequest request) {
        return parser.parseSuggestion(ask(image, OllamaVisionTagger.reassessPrompt(request), reassessSchema()));
    }

    private String ask(byte[] image, String prompt, Map<String, Object> schema) {
        String encoded = Base64.getEncoder().encodeToString(OllamaVisionTagger.shrink(image, maxSide));
        waitForTurn();
        JsonNode response;
        try {
            response = client.post()
                    .uri("/v1beta/models/{model}:generateContent", model)
                    .body(requestBody(prompt, encoded, schema))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new LlmUnavailableException(describeFailure(e.getStatusCode().value()), e);
        } catch (RestClientException e) {
            throw new LlmUnavailableException("Gemini could not be reached: " + e.getMessage(), e);
        }
        return answerText(response);
    }

    /** One call at a time with a pause in between, so a queue of pictures stays under the free tier's per-minute limit. */
    private void waitForTurn() {
        synchronized (spacing) {
            long now = System.nanoTime();
            long wait = nextCallAt - now;
            if (wait > 0) {
                try {
                    Thread.sleep(wait / 1_000_000, (int) (wait % 1_000_000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new LlmUnavailableException("Interrupted while waiting to call Gemini", e);
                }
            }
            nextCallAt = System.nanoTime() + minIntervalNanos;
        }
    }

    static String describeFailure(int status) {
        return switch (status) {
            case 429 -> "Gemini's quota is used up (the free tier limits calls per minute and per day); try again later";
            case 400, 403 -> "Gemini refused the request (HTTP " + status + "); check GEMINI_API_KEY and the model name";
            case 404 -> "Gemini does not know this model (HTTP 404); check wtm.vision.gemini.model";
            default -> "Gemini answered with HTTP " + status;
        };
    }

    static Map<String, Object> requestBody(String prompt, String encodedJpeg, Map<String, Object> schema) {
        return Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", SYSTEM_PROMPT))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(
                        Map.of("text", prompt),
                        Map.of("inlineData", Map.of("mimeType", "image/jpeg", "data", encodedJpeg))))),
                "generationConfig", Map.of(
                        "temperature", 0.2,
                        "maxOutputTokens", MAX_ANSWER_TOKENS,
                        "responseMimeType", "application/json",
                        "responseJsonSchema", schema));
    }

    /** The text of the first answer, or a refusal to use it when Gemini blocked or cut it. */
    static String answerText(JsonNode response) {
        if (response == null) {
            throw new LlmUnavailableException("Gemini returned an empty response");
        }
        String blocked = response.path("promptFeedback").path("blockReason").asText("");
        if (!blocked.isEmpty()) {
            throw new LlmUnavailableException("Gemini would not look at this picture (" + blocked + ")");
        }
        JsonNode candidate = response.path("candidates").path(0);
        String finish = candidate.path("finishReason").asText("");
        if (!finish.isEmpty() && !finish.equals("STOP")) {
            throw new LlmUnavailableException("Gemini stopped before it finished (" + finish + ")");
        }
        StringBuilder text = new StringBuilder();
        candidate.path("content").path("parts").forEach(part -> text.append(part.path("text").asText("")));
        if (text.isEmpty()) {
            throw new LlmUnavailableException("Gemini answered with no text");
        }
        return text.toString();
    }

    static String userPrompt(String hint, Reference reference) {
        StringBuilder sb = new StringBuilder("""
                請分析這張圖片,輸出 JSON,全部使用繁體中文(台灣用語):
                isMeme:這是梗圖、迷因、反應圖或貼圖嗎?(一般照片、風景、自拍、廣告、單純的截圖、圖表都是 false)
                title:通用的梗名稱,最多 20 字
                meaning:如果你認得這個梗,用 2 到 3 句說明它的由來(出自哪個作品、人物或事件)與意思、為什麼好笑;不確定出處就只說它表達什麼,不要編造
                usageExamples:3 個人們會在什麼情境用到它,用口語,每個不超過 25 字
                emotions:1 到 4 個情緒詞
                tags:3 到 8 個關鍵字:圖中有什麼人事物、主題、梗的類型
                imageText:圖片裡出現的文字,照原樣抄下來;沒有文字就給空字串
                """);
        OllamaVisionTagger.appendHintAndReference(sb, hint, reference);
        return sb.toString();
    }

    static Map<String, Object> describeSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("isMeme", Map.of("type", "boolean"));
        properties.put("title", Map.of("type", "string"));
        properties.put("meaning", Map.of("type", "string"));
        properties.put("usageExamples", strings(5));
        properties.put("emotions", strings(4));
        properties.put("tags", strings(8));
        properties.put("imageText", Map.of("type", "string"));
        return object(properties);
    }

    static Map<String, Object> reassessSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("isMeme", Map.of("type", "boolean"));
        properties.put("meaning", Map.of("type", "string"));
        properties.put("usageExamples", strings(5));
        properties.put("emotions", strings(4));
        properties.put("tags", strings(8));
        properties.put("imageText", Map.of("type", "string"));
        properties.put("reasoning", Map.of("type", "string"));
        properties.put("verdict", Map.of("type", "string", "enum", List.of("KEEP", "CHANGE")));
        return object(properties);
    }

    private static Map<String, Object> strings(int maxItems) {
        return Map.of("type", "array", "items", Map.of("type", "string"), "maxItems", maxItems);
    }

    private static Map<String, Object> object(Map<String, Object> properties) {
        return Map.of("type", "object", "properties", properties, "required", List.copyOf(properties.keySet()));
    }
}
