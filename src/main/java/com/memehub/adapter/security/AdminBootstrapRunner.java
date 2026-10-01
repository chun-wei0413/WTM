package com.memehub.adapter.security;

import com.memehub.application.auth.BootstrapAdminHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the first administrator at startup when none exists yet.
 */
@Component
class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final BootstrapAdminHandler handler;
    private final SecurityProperties properties;

    AdminBootstrapRunner(BootstrapAdminHandler handler, SecurityProperties properties) {
        this.handler = handler;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        var admin = properties.bootstrapAdmin();
        if (admin.password() == null || admin.password().isBlank()) {
            log.warn("No bootstrap admin password configured; skipping admin creation");
            return;
        }
        if (handler.handle(admin.username(), admin.password())) {
            log.info("Created bootstrap administrator '{}'", admin.username());
        }
    }
}
