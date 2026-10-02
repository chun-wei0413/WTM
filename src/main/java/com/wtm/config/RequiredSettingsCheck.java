package com.wtm.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * Stops the application at startup when a secret is missing.
 *
 * <p>Spring leaves an unresolved {@code ${NAME}} in a bound property as literal text instead of
 * failing, so a forgotten admin password would silently become the string
 * {@code ${WTM_ADMIN_PASSWORD}}. This runs before any bean (and so before the database
 * connection) is created and refuses to continue.
 */
class RequiredSettingsCheck implements BeanFactoryPostProcessor, EnvironmentAware {

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
