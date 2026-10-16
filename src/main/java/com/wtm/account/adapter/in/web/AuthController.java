package com.wtm.account.adapter.in.web;

import com.wtm.account.entity.User;
import com.wtm.account.usecase.port.in.command.LoginUseCase;
import com.wtm.account.usecase.port.in.command.RegisterUserUseCase;
import com.wtm.account.usecase.port.out.TokenIssuer.IssuedToken;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final LoginUseCase login;
    private final RegisterUserUseCase register;

    AuthController(LoginUseCase login, RegisterUserUseCase register) {
        this.login = login;
        this.register = register;
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        IssuedToken token = login.handle(request.username(), request.password(), http.getRemoteAddr());
        return new LoginResponse(token.value(), token.expiresAt());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    RegisteredResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        User user = register.handle(request.username(), request.password(), http.getRemoteAddr());
        return new RegisteredResponse(user.id(), user.username());
    }

    record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    record LoginResponse(String token, Instant expiresAt) {
    }

    record RegisterRequest(@NotBlank String username, @NotBlank String password) {
    }

    record RegisteredResponse(UUID id, String username) {
    }
}
