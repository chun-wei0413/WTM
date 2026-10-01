package com.memehub.adapter.in.web;

import com.memehub.application.auth.LoginHandler;
import com.memehub.application.port.out.TokenIssuer.IssuedToken;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final LoginHandler login;

    AuthController(LoginHandler login) {
        this.login = login;
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        IssuedToken token = login.handle(request.username(), request.password());
        return new LoginResponse(token.value(), token.expiresAt());
    }

    record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    record LoginResponse(String token, Instant expiresAt) {
    }
}
