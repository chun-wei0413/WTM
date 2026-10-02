package com.wtm.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StructuredOutputTest {

    private final ObjectMapper json = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> properties(Map<String, Object> schema) {
        return (Map<String, Map<String, Object>>) schema.get("properties");
    }

    @Test
    void everyFieldTheParserReadsIsRequiredBySchema() {
        assertThat(OllamaVisionTagger.describeSchema().get("required"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .containsExactlyInAnyOrder("isMeme", "title", "meaning", "usageExamples", "emotions", "tags", "imageText");
        assertThat(OllamaVisionTagger.reassessSchema().get("required"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .containsExactlyInAnyOrder("isMeme", "meaning", "usageExamples", "emotions", "tags", "imageText",
                        "reasoning", "verdict");
    }

    @Test
    void everyTextAndListHasALengthLimitSoTheAnswerAlwaysEnds() {
        for (var schema : List.of(OllamaVisionTagger.describeSchema(), OllamaVisionTagger.reassessSchema())) {
            properties(schema).forEach((name, field) -> {
                switch ((String) field.get("type")) {
                    case "string" -> {
                        if (!field.containsKey("enum")) {
                            assertThat(field).as(name).containsKey("maxLength");
                        }
                    }
                    case "array" -> {
                        assertThat(field).as(name).containsKey("maxItems");
                        assertThat(((Map<?, ?>) field.get("items")).containsKey("maxLength")).as(name + " items").isTrue();
                    }
                    default -> { }
                }
            });
        }
    }

    @Test
    void theVerdictCanOnlyBeKeepOrChange() {
        assertThat(properties(OllamaVisionTagger.reassessSchema()).get("verdict").get("enum"))
                .isEqualTo(List.of("KEEP", "CHANGE"));
    }

    @Test
    void theSchemaIsSomethingJsonCanCarry() throws Exception {
        String text = json.writeValueAsString(OllamaVisionTagger.describeSchema());

        assertThat(json.readTree(text).get("properties").get("tags").get("maxItems").asInt()).isEqualTo(8);
    }

    @Test
    void wordsRepeatedLineAfterLineInAPictureAreKeptOnce() throws Exception {
        var node = json.readTree("{\"imageText\": \"Don't eat 不可食用\\nDon't eat 不可食用\\nDon't eat 不可食用\\n小心\\n\\nDon't eat 不可食用\"}")
                .get("imageText");

        assertThat(VisionAnswerParser.pictureText(node)).isEqualTo("Don't eat 不可食用\n小心\nDon't eat 不可食用");
    }

    @Test
    void textWithoutRepeatsIsLeftAlone() throws Exception {
        var node = json.readTree("{\"imageText\": \"我不要\\n我不要\\n好\"}").get("imageText");

        assertThat(VisionAnswerParser.pictureText(node)).isEqualTo("我不要\n好");
        assertThat(VisionAnswerParser.pictureText(null)).isEmpty();
    }
}
