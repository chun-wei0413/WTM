package com.wtm.account.adapter.in.web;

import com.wtm.account.usecase.port.in.command.LoginUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class LoginController {

    private final LoginUseCase login;

    LoginController(LoginUseCase login) {
        this.login = login;
    }

    @PostMapping("/api/auth/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        LoginUseCase.Output token = login.handle(new LoginUseCase.Input(request.username(), request.password(), http.getRemoteAddr()));
        return new LoginResponse(token.token(), token.expiresAt());
    }

    record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    record LoginResponse(String token, Instant expiresAt) {
    }
}
