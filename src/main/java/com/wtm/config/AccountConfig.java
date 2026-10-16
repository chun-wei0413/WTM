package com.wtm.config;

import com.wtm.account.adapter.security.SecurityProperties;
import com.wtm.account.entity.LoginPolicy;
import com.wtm.account.entity.RegistrationPolicy;
import com.wtm.account.usecase.port.out.PasswordHasher;
import com.wtm.account.usecase.port.out.TokenIssuer;
import com.wtm.account.usecase.port.out.UserRepository;
import com.wtm.account.usecase.service.BootstrapAdminService;
import com.wtm.account.usecase.service.LoginService;
import com.wtm.account.usecase.service.RegisterUserService;
import com.wtm.shared.RateLimiterPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the framework-free services of Account into the Spring context.
 */
@Configuration
class AccountConfig {

    @Bean
    LoginService loginService(UserRepository users, PasswordHasher hasher, TokenIssuer tokens,
                              RateLimiterPort limiter, SecurityProperties security) {
        var throttling = security.throttling();
        return new LoginService(users, hasher, tokens, limiter, new LoginPolicy(
                throttling.loginFailuresPerAccount(), throttling.loginFailuresPerAddress(), throttling.loginWindow()));
    }

    @Bean
    RegisterUserService registerUserService(UserRepository users, PasswordHasher hasher,
                                            RateLimiterPort limiter, SecurityProperties security) {
        var throttling = security.throttling();
        return new RegisterUserService(users, hasher, limiter, new RegistrationPolicy(
                security.registrationEnabled(), throttling.registrationsPerAddress(), throttling.registrationWindow()));
    }

    @Bean
    BootstrapAdminService bootstrapAdminService(UserRepository users, PasswordHasher hasher) {
        return new BootstrapAdminService(users, hasher);
    }
}
