package com.memehub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RequiredSettingsConfig {

    // static: a BeanFactoryPostProcessor must be created before the configuration class itself.
    @Bean
    static RequiredSettingsCheck requiredSettingsCheck() {
        return new RequiredSettingsCheck();
    }
}
