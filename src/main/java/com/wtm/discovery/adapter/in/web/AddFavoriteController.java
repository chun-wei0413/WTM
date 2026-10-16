package com.wtm.discovery.adapter.in.web;

import com.wtm.discovery.usecase.port.in.command.FavoritesUseCase;
import com.wtm.shared.UserId;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AddFavoriteController {

    private final FavoritesUseCase favorites;

    AddFavoriteController(FavoritesUseCase favorites) {
        this.favorites = favorites;
    }

    @PutMapping("/api/favorites/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void add(@PathVariable UUID templateId, @AuthenticationPrincipal Jwt jwt) {
        favorites.add(new UserId(UUID.fromString(jwt.getSubject())), templateId);
    }
}
