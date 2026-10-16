package com.wtm.account.adapter.in.web;

import com.wtm.account.usecase.port.in.command.RegisterUserUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class RegisterController {

    private final RegisterUserUseCase register;

    RegisterController(RegisterUserUseCase register) {
        this.register = register;
    }

    @PostMapping("/api/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    RegisteredResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        RegisterUserUseCase.Output user = register.handle(new RegisterUserUseCase.Input(request.username(), request.password(), http.getRemoteAddr()));
        return new RegisteredResponse(user.id(), user.username());
    }

    record RegisterRequest(@NotBlank String username, @NotBlank String password) {
    }

    record RegisteredResponse(UUID id, String username) {
    }
}
