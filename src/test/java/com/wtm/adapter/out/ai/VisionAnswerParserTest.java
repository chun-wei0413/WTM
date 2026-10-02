package com.wtm.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtm.application.collection.ImageTags;
import com.wtm.application.port.out.LlmUnavailableException;
import org.junit.jupiter.api.Test;

class VisionAnswerParserTest {

    private final VisionAnswerParser parser = new VisionAnswerParser(new ObjectMapper());

    private static final String GOOD = """
            {"isMeme": true, "title": "分心男友", "meaning": "被新的事物吸引而冷落舊的",
             "usageExamples": ["看到新框架就想放棄舊專案", "新手機一出就心動"],
             "emotions": ["誘惑", "喜新厭舊"], "tags": ["男友", "路人", "劈腿"], "imageText": ""}""";

    @Test
    void readsACompleteAnswer() {
        ImageTags tags = parser.parse(GOOD);

        assertThat(tags.isMeme()).isTrue();
        assertThat(tags.title()).isEqualTo("分心男友");
        assertThat(tags.meaning()).contains("新的事物");
        assertThat(tags.usageExamples()).hasSize(2);
        assertThat(tags.emotions()).containsExactly("誘惑", "喜新厭舊");
        assertThat(tags.tags()).containsExactly("男友", "路人", "劈腿");
        assertThat(tags.imageText()).isEmpty();
    }

    @Test
    void toleratesCodeFencesAndChatter() {
        ImageTags tags = parser.parse("好的,這是分析結果:\n```json\n" + GOOD + "\n```\n希望有幫助");

        assertThat(tags.title()).isEqualTo("分心男友");
    }

    @Test
    void acceptsASingleStringWhereAListWasAsked() {
        ImageTags tags = parser.parse("""
                {"isMeme": true, "meaning": "x", "usageExamples": "情境一;情境二",
                 "emotions": "無奈、崩潰", "tags": "貓, 沙發, 發呆"}""");

        assertThat(tags.usageExamples()).containsExactly("情境一", "情境二");
        assertThat(tags.emotions()).containsExactly("無奈", "崩潰");
        assertThat(tags.tags()).containsExactly("貓", "沙發", "發呆");
    }

    @Test
    void dropsBlankDuplicateAndExcessListItems() {
        ImageTags tags = parser.parse("""
                {"isMeme": true, "meaning": "x",
                 "tags": ["a", " ", "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l"]}""");

        assertThat(tags.tags()).doesNotHaveDuplicates().doesNotContain("").hasSize(10);
    }

    @Test
    void understandsNotAMemeInSeveralWays() {
        assertThat(parser.parse("{\"isMeme\": false}").isMeme()).isFalse();
        assertThat(parser.parse("{\"isMeme\": \"false\"}").isMeme()).isFalse();
        assertThat(parser.parse("{\"isMeme\": \"否\"}").isMeme()).isFalse();
    }

    @Test
    void anAnswerThatDoesNotSayIsAssumedToBeAMemeButNeedsAMeaning() {
        assertThat(parser.parse("{\"meaning\": \"x\"}").isMeme()).isTrue();
        assertThatThrownBy(() -> parser.parse("{\"title\": \"只有標題\"}")).isInstanceOf(LlmUnavailableException.class);
    }

    @Test
    void aMemeWithoutAMeaningIsUnusable() {
        assertThatThrownBy(() -> parser.parse("{\"isMeme\": true, \"meaning\": \"  \"}"))
                .isInstanceOf(LlmUnavailableException.class);
    }

    @Test
    void notAMemeNeedsNoMeaning() {
        ImageTags tags = parser.parse("{\"isMeme\": false, \"title\": \"風景照\"}");

        assertThat(tags.isMeme()).isFalse();
    }

    @Test
    void refusesWhatIsNotJson() {
        assertThatThrownBy(() -> parser.parse("抱歉,我無法分析這張圖片")).isInstanceOf(LlmUnavailableException.class);
        assertThatThrownBy(() -> parser.parse("{\"isMeme\": true, ")).isInstanceOf(LlmUnavailableException.class);
        assertThatThrownBy(() -> parser.parse(null)).isInstanceOf(LlmUnavailableException.class);
    }
}
