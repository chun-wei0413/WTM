package com.wtm.adapter.out.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtm.application.collection.ImageTags;
import com.wtm.application.port.out.LlmUnavailableException;
import com.wtm.application.report.Suggestion;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns the vision model's reply into {@link ImageTags}. Models wrap JSON in prose or code fences,
 * leave fields out, and sometimes give a single string where a list was asked for, so this
 * accepts all of that and only refuses what is genuinely unusable.
 */
final class VisionAnswerParser {

    private static final int MAX_LIST_ITEMS = 10;
    private static final int MAX_ITEM_LENGTH = 80;

    private final ObjectMapper json;

    VisionAnswerParser(ObjectMapper json) {
        this.json = json;
    }

    ImageTags parse(String answer) {
        JsonNode root = readObject(answer);
        boolean isMeme = readBoolean(root.get("isMeme"));
        ImageTags tags = new ImageTags(isMeme, text(root.get("title")), text(root.get("meaning")),
                list(root.get("usageExamples")), list(root.get("emotions")), list(root.get("tags")),
                pictureText(root.get("imageText")));
        if (isMeme && tags.meaning().isBlank()) {
            throw new LlmUnavailableException("The vision model called it a meme but gave no meaning");
        }
        return tags;
    }

    /** Like {@link #parse}, for the answer to "look again": the same fields plus the model's own reasoning. */
    Suggestion parseSuggestion(String answer) {
        JsonNode root = readObject(answer);
        boolean isMeme = readBoolean(root.get("isMeme"));
        Suggestion suggestion = new Suggestion(isMeme, text(root.get("meaning")), list(root.get("usageExamples")),
                list(root.get("emotions")), list(root.get("tags")), pictureText(root.get("imageText")),
                text(root.get("reasoning")), text(root.get("verdict")).toUpperCase().startsWith("KEEP"));
        if (isMeme && suggestion.meaning().isBlank()) {
            throw new LlmUnavailableException("The vision model proposed no meaning");
        }
        return suggestion;
    }

    private JsonNode readObject(String answer) {
        int start = answer == null ? -1 : answer.indexOf('{');
        int end = answer == null ? -1 : answer.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new LlmUnavailableException("The vision model did not answer with JSON");
        }
        try {
            return json.readTree(answer.substring(start, end + 1));
        } catch (JsonProcessingException e) {
            throw new LlmUnavailableException("The vision model's JSON answer could not be read", e);
        }
    }

    private static boolean readBoolean(JsonNode node) {
        if (node == null || node.isNull()) {
            return true;   // not said: assume it is, the tags will show whether it was a sensible answer
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        String text = node.asText().strip().toLowerCase();
        return !(text.equals("false") || text.equals("no") || text.equals("否") || text.equals("不是"));
    }

    /** Text copied from a picture, with lines that repeat the one before left out. */
    static String pictureText(JsonNode node) {
        StringBuilder out = new StringBuilder();
        String previous = null;
        for (String line : text(node).split("\\R")) {
            String stripped = line.strip();
            if (!stripped.isEmpty() && !stripped.equals(previous)) {
                if (out.length() > 0) {
                    out.append('\n');
                }
                out.append(stripped);
            }
            previous = stripped;
        }
        return out.toString();
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? "" : node.asText("").strip();
    }

    private static List<String> list(JsonNode node) {
        List<String> items = new ArrayList<>();
        if (node == null || node.isNull()) {
            return items;
        }
        if (node.isArray()) {
            node.forEach(n -> addItem(items, n.asText("")));
        } else {
            for (String part : node.asText("").split("[,，、;；\\n]")) {
                addItem(items, part);
            }
        }
        return items;
    }

    private static void addItem(List<String> items, String raw) {
        String item = raw.strip();
        if (item.isEmpty() || items.size() >= MAX_LIST_ITEMS || items.contains(item)) {
            return;
        }
        items.add(item.codePointCount(0, item.length()) > MAX_ITEM_LENGTH
                ? item.substring(0, item.offsetByCodePoints(0, MAX_ITEM_LENGTH))
                : item);
    }
}
