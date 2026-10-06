package com.wtm.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * Stops the application at startup when a secret is missing or a model provider is not one that exists, and says at
 * startup which model providers are in use and what else could be chosen.
 *
 * <p>Spring leaves an unresolved {@code ${NAME}} in a bound property as literal text instead of
 * failing, so a forgotten admin password would silently become the string
 * {@code ${WTM_ADMIN_PASSWORD}}. This runs before any bean (and so before the database
 * connection) is created and refuses to continue.
 */
class RequiredSettingsCheck implements BeanFactoryPostProcessor, EnvironmentAware {

    private static final Logger log = LoggerFactory.getLogger(RequiredSettingsCheck.class);

    /** The value shipped in .env.example; copying that file unchanged must not start the app. */
    static final String EXAMPLE_VALUE = "change-me";

    /** property name -> environment variable that normally supplies it */
    static final Map<String, String> REQUIRED = new LinkedHashMap<>();

    static {
        REQUIRED.put("spring.datasource.password", "DB_PASSWORD");
        REQUIRED.put("wtm.storage.access-key", "S3_ACCESS_KEY");
        REQUIRED.put("wtm.storage.secret-key", "S3_SECRET_KEY");
        REQUIRED.put("wtm.security.jwt.secret", "WTM_JWT_SECRET");
        REQUIRED.put("wtm.security.bootstrap-admin.password", "WTM_ADMIN_PASSWORD");
    }

    /** property name -> the environment variable that sets it and the values it may have (the first is the default) */
    record Choice(String variable, List<String> values) {
    }

    static final Map<String, Choice> CHOICES = new LinkedHashMap<>();

    static {
        CHOICES.put("wtm.vision.provider", new Choice("WTM_VISION_PROVIDER", List.of("mock", "ollama", "gemini")));
        CHOICES.put("wtm.embedding.provider", new Choice("WTM_EMBEDDING_PROVIDER", List.of("mock", "ollama")));
        CHOICES.put("wtm.explainer.provider", new Choice("WTM_EXPLAINER_PROVIDER", List.of("mock", "ollama")));
    }

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        List<String> problems = problems(environment);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Missing required settings:\n  - "
                    + String.join("\n  - ", problems)
                    + "\nRun scripts/init-env.ps1 to create a .env file with generated values,"
                    + " or provide them as environment variables.");
        }
        List<String> wrongChoices = choiceProblems(environment);
        if (!wrongChoices.isEmpty()) {
            throw new IllegalStateException("Unknown model provider:\n  - " + String.join("\n  - ", wrongChoices)
                    + "\nSet the variable in .env or in the environment to one of the values listed.");
        }
        CHOICES.forEach((property, choice) -> log.info("{} = {}   (choose one of: {})", choice.variable(),
                environment.getProperty(property), String.join(" | ", choice.values())));
        if ("mock".equalsIgnoreCase(environment.getProperty("wtm.vision.provider"))) {
            log.warn("WTM_VISION_PROVIDER is mock: new pictures get made-up descriptions and no model looks at them."
                    + " Set it to ollama (local) or gemini (Google) for real ones.");
        }
    }

    /** The model providers that are set to something that does not exist, each with what could be chosen instead. */
    static List<String> choiceProblems(Environment environment) {
        List<String> problems = new ArrayList<>();
        CHOICES.forEach((property, choice) -> {
            String value = environment.getProperty(property);
            if (value != null && choice.values().stream().noneMatch(v -> v.equalsIgnoreCase(value.strip()))) {
                problems.add(choice.variable() + "=" + value + " is not valid; choose one of: "
                        + String.join(" | ", choice.values()));
            }
        });
        return problems;
    }

    static List<String> problems(Environment environment) {
        List<String> problems = new ArrayList<>();
        REQUIRED.forEach((property, variable) -> {
            String value;
            try {
                value = environment.getProperty(property);
            } catch (IllegalArgumentException unresolvedPlaceholder) {
                value = null;
            }
            if (value == null || value.isBlank() || value.equals(EXAMPLE_VALUE)) {
                problems.add(variable + " (" + property + ")");
            }
        });
        return problems;
    }
}
