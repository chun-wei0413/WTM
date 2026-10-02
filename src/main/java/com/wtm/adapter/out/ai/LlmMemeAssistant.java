package com.wtm.adapter.out.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtm.application.port.out.LlmPort;
import com.wtm.application.port.out.LlmUnavailableException;
import com.wtm.application.port.out.MemeAssistantPort;
import com.wtm.domain.template.Slot;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Writes captions with a language model: builds the prompt, asks for a JSON answer,
 * and turns whatever came back into captions by slot number.
 */
@Component
@ConditionalOnProperty(name = "wtm.llm.provider", havingValue = "ollama")
class LlmMemeAssistant implements MemeAssistantPort {

    static final String SYSTEM_PROMPT = """
            你是熟悉台灣網路文化的迷因(梗圖)文案寫手。
            請用繁體中文(台灣用語)寫出簡短、有梗、貼近使用者情境的文案,不要解釋。
            只輸出一個 JSON 物件,不要輸出任何其他文字。""";

    private final LlmPort llm;
    private final ObjectMapper json;

    LlmMemeAssistant(LlmPort llm, ObjectMapper json) {
        this.llm = llm;
        this.json = json;
    }

    @Override
    public Map<Integer, String> writeCaptions(String situation, CaptionBrief brief) {
        String answer = llm.complete(SYSTEM_PROMPT, userPrompt(situation, brief));
        return parse(answer, brief);
    }

    static String userPrompt(String situation, CaptionBrief brief) {
        StringBuilder sb = new StringBuilder();
        sb.append("使用者想表達的情境:").append(situation).append("\n\n");
        sb.append("梗圖模板:").append(brief.templateName()).append('\n');
        sb.append("這個梗的意思:").append(brief.meaning()).append('\n');
        if (!brief.usageExamples().isEmpty()) {
            sb.append("典型的使用方式:\n");
            brief.usageExamples().forEach(e -> sb.append("- ").append(e).append('\n'));
        }
        sb.append("\n文字格(請依編號填寫):\n");
        for (Slot slot : brief.slots()) {
            sb.append(slot.slotNo()).append(". 角色:").append(slot.role())
                    .append(";最多 ").append(slot.maxChars()).append(" 個字;")
                    .append(slot.required() ? "必填" : "選填").append('\n');
        }
        sb.append("\n請輸出 JSON:key 是文字格編號,value 是該格的文案,每格不得超過字數上限。")
                .append("例如:{\"1\":\"文案\",\"2\":\"文案\"}");
        return sb.toString();
    }

    /** Accepts answers wrapped in code fences or chatter by reading from the first '{' to the last '}'. */
    Map<Integer, String> parse(String answer, CaptionBrief brief) {
        int start = answer == null ? -1 : answer.indexOf('{');
        int end = answer == null ? -1 : answer.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new LlmUnavailableException("The model did not answer with JSON");
        }
        JsonNode root;
        try {
            root = json.readTree(answer.substring(start, end + 1));
        } catch (JsonProcessingException e) {
            throw new LlmUnavailableException("The model's JSON answer could not be read", e);
        }

        Map<Integer, String> captions = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            try {
                int slotNo = Integer.parseInt(field.getKey().strip());
                boolean known = brief.slots().stream().anyMatch(s -> s.slotNo() == slotNo);
                if (known && field.getValue().isTextual() && !field.getValue().asText().isBlank()) {
                    captions.put(slotNo, field.getValue().asText().strip());
                }
            } catch (NumberFormatException ignored) {
                // A key that is not a slot number is simply not a caption.
            }
        }
        if (captions.isEmpty()) {
            throw new LlmUnavailableException("The model did not write any caption");
        }
        return captions;
    }
}
