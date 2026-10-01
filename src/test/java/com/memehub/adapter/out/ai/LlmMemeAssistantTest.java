package com.memehub.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memehub.application.port.out.LlmUnavailableException;
import com.memehub.application.port.out.MemeAssistantPort.CaptionBrief;
import com.memehub.domain.template.Slot;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LlmMemeAssistantTest {

    private static final CaptionBrief BRIEF = new CaptionBrief("Drake", "拒絕一件事,偏好另一件事",
            List.of("不想寫文件,只想直接寫程式"),
            List.of(new Slot(1, "被拒絕的事物", 14, true, 0, 0, 100, 50),
                    new Slot(2, "偏好的事物", 14, true, 0, 50, 100, 50)));

    private static LlmMemeAssistant assistantAnswering(String answer) {
        return new LlmMemeAssistant((system, user) -> answer, new ObjectMapper());
    }

    private static Map<Integer, String> run(String answer) {
        return assistantAnswering(answer).writeCaptions("週一又要上班", BRIEF);
    }

    @Test
    void readsPlainJson() {
        assertThat(run("{\"1\":\"上班\",\"2\":\"放假\"}")).containsEntry(1, "上班").containsEntry(2, "放假");
    }

    @Test
    void toleratesCodeFencesAndChatter() {
        String answer = "好的,這是文案:\n```json\n{\"1\": \"上班\", \"2\": \"放假\"}\n```\n希望你喜歡!";

        assertThat(run(answer)).containsEntry(1, "上班").containsEntry(2, "放假");
    }

    @Test
    void ignoresSlotsTheTemplateDoesNotHaveAndKeysThatAreNotNumbers() {
        Map<Integer, String> captions = run("{\"1\":\"上班\",\"7\":\"多的\",\"note\":\"說明\"}");

        assertThat(captions).containsOnlyKeys(1);
    }

    @Test
    void ignoresBlankAndNonTextValues() {
        assertThat(run("{\"1\":\"上班\",\"2\":\"  \"}")).containsOnlyKeys(1);
        assertThat(run("{\"1\":\"上班\",\"2\":42}")).containsOnlyKeys(1);
    }

    @Test
    void failsWhenThereIsNoJson() {
        assertThatThrownBy(() -> run("抱歉,我無法完成這個要求"))
                .isInstanceOf(LlmUnavailableException.class);
    }

    @Test
    void failsWhenTheJsonIsBroken() {
        assertThatThrownBy(() -> run("{\"1\": \"上班\", "))
                .isInstanceOf(LlmUnavailableException.class);
        assertThatThrownBy(() -> run("{\"1\": 上班}"))
                .isInstanceOf(LlmUnavailableException.class);
    }

    @Test
    void failsWhenNoCaptionSurvives() {
        assertThatThrownBy(() -> run("{\"9\":\"沒有這格\"}"))
                .isInstanceOf(LlmUnavailableException.class);
    }

    @Test
    void promptCarriesTheSituationTheTemplateAndTheLimits() {
        String prompt = LlmMemeAssistant.userPrompt("週一又要上班", BRIEF);

        assertThat(prompt).contains("週一又要上班", "Drake", "拒絕一件事", "不想寫文件",
                "被拒絕的事物", "14", "必填", "JSON");
    }
}
