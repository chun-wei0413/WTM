package com.wtm.discovery.adapter.in.web;

import com.wtm.discovery.usecase.port.in.command.FavoritesUseCase;
import com.wtm.shared.UserId;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class RemoveFavoriteController {

    private final FavoritesUseCase favorites;

    RemoveFavoriteController(FavoritesUseCase favorites) {
        this.favorites = favorites;
    }

    @DeleteMapping("/api/favorites/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable UUID templateId, @AuthenticationPrincipal Jwt jwt) {
        favorites.remove(new UserId(UUID.fromString(jwt.getSubject())), templateId);
    }
}
