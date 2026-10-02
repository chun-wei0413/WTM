package com.usethatmeme.adapter.out.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usethatmeme.application.collection.ImageTags;
import com.usethatmeme.application.port.out.LlmUnavailableException;
import com.usethatmeme.application.port.out.VisionTaggerPort;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Asks a vision model served by Ollama to catalogue a picture: is it a meme, what does it mean,
 * when would people use it, what words are in it.
 */
@Component
@ConditionalOnProperty(name = "usethatmeme.vision.provider", havingValue = "ollama")
class OllamaVisionTagger implements VisionTaggerPort {

    static final String SYSTEM_PROMPT = """
            你是梗圖(迷因)資料庫的編目員,熟悉台灣與全球的網路梗、反應圖與貼圖。
            看完圖片後,只輸出一個 JSON 物件,不要輸出任何其他文字。""";

    private final RestClient client;
    private final String model;
    private final int maxSide;
    private final VisionAnswerParser parser;

    OllamaVisionTagger(VisionProperties properties, ObjectMapper json) {
        var ollama = properties.ollama();
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(3));
        factory.setReadTimeout(ollama.timeout());
        this.client = RestClient.builder().baseUrl(ollama.baseUrl()).requestFactory(factory).build();
        this.model = ollama.model();
        this.maxSide = ollama.maxSide();
        this.parser = new VisionAnswerParser(json);
    }

    @Override
    public ImageTags describe(byte[] image, String contentType, String hint) {
        String encoded = Base64.getEncoder().encodeToString(shrink(image, maxSide));
        Map<String, Object> body = Map.of(
                "model", model,
                "stream", false,
                "format", "json",
                "options", Map.of("temperature", 0.2),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userPrompt(hint), "images", List.of(encoded))));
        try {
            JsonNode response = client.post().uri("/api/chat").body(body).retrieve().body(JsonNode.class);
            if (response == null || !response.path("message").has("content")) {
                throw new LlmUnavailableException("Ollama returned an unexpected response");
            }
            return parser.parse(response.path("message").path("content").asText());
        } catch (RestClientException e) {
            throw new LlmUnavailableException("Ollama request failed: " + e.getMessage(), e);
        }
    }

    static String userPrompt(String hint) {
        StringBuilder sb = new StringBuilder("""
                請分析這張圖片,輸出 JSON,欄位如下,全部使用繁體中文(台灣用語):
                {
                  "isMeme": 這是梗圖、迷因、反應圖或貼圖嗎?(true 或 false。一般照片、風景、自拍、廣告、單純的截圖、圖表都是 false),
                  "title": 簡短的名稱,最多 20 字;如果是大家熟悉的梗,請用通用的名稱,
                  "meaning": 這張圖在表達什麼、為什麼好笑,1 到 2 句,
                  "usageExamples": 3 個人們會在什麼情境用到它,用口語,每個不超過 25 字,
                  "emotions": 1 到 4 個情緒詞,例如「無奈」「得意」「崩潰」,
                  "tags": 3 到 8 個關鍵字:圖中有什麼人事物、主題、梗的類型,
                  "imageText": 圖片裡出現的文字,照原樣抄下來;沒有文字就給空字串
                }
                """);
        if (hint != null && !hint.isBlank()) {
            sb.append("\n圖片來源給的標題是「").append(hint).append("」,可以參考,但以圖片內容為準。\n");
        }
        return sb.toString();
    }

    /** Shrinks the picture so the model is not fed more pixels than it can use; GIFs become one frame. */
    static byte[] shrink(byte[] image, int maxSide) {
        try {
            BufferedImage source = javax.imageio.ImageIO.read(new ByteArrayInputStream(image));
            if (source == null) {
                throw new LlmUnavailableException("The picture could not be decoded");
            }
            double scale = Math.min(1.0, (double) maxSide / Math.max(source.getWidth(), source.getHeight()));
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
            BufferedImage flat = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = flat.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(source, 0, 0, width, height, null);
            g.dispose();
            var out = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(flat, "jpg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new LlmUnavailableException("The picture could not be prepared for the vision model", e);
        }
    }
}
