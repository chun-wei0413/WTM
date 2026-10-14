package com.wtm.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.PropertySource;
import org.springframework.mock.env.MockEnvironment;

/**
 * The model providers are chosen with WTM_VISION_PROVIDER and its two siblings. A line of .env arrives in Spring as a plain
 * property with that name, and a name written in capitals with underscores does not bind to wtm.vision.provider by itself
 * (only a real operating-system variable gets that treatment). So it is the placeholders in application.yml that make
 * .env work, and this test holds them. It reads application.yml alone, so the .env of the machine it runs on cannot answer.
 */
class ProviderSettingsTest {

    private static MockEnvironment environmentWith(String... settings) throws IOException {
        MockEnvironment environment = new MockEnvironment();
        for (String setting : settings) {
            String[] nameAndValue = setting.split("=", 2);
            environment.setProperty(nameAndValue[0], nameAndValue[1]);
        }
        for (PropertySource<?> source : new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))) {
            environment.getPropertySources().addLast(source);
        }
        return environment;
    }

    @Test
    void aLineOfDotEnvChoosesTheProvider() throws IOException {
        MockEnvironment environment = environmentWith("WTM_VISION_PROVIDER=gemini", "WTM_EMBEDDING_PROVIDER=ollama",
                "WTM_EXPLAINER_PROVIDER=ollama");

        assertThat(environment.getProperty("wtm.vision.provider")).isEqualTo("gemini");
        assertThat(environment.getProperty("wtm.embedding.provider")).isEqualTo("ollama");
        assertThat(environment.getProperty("wtm.explainer.provider")).isEqualTo("ollama");
    }

    @Test
    void withNothingSetEveryProviderIsTheMock() throws IOException {
        MockEnvironment environment = environmentWith();

        assertThat(environment.getProperty("wtm.vision.provider")).isEqualTo("mock");
        assertThat(environment.getProperty("wtm.embedding.provider")).isEqualTo("mock");
        assertThat(environment.getProperty("wtm.explainer.provider")).isEqualTo("mock");
    }
}
