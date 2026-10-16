package com.wtm.discovery.adapter.in.web;

import com.wtm.discovery.usecase.port.in.command.FavoritesUseCase;
import com.wtm.discovery.usecase.port.in.query.LibraryItem;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's own favorites. */
@RestController
class ListFavoritesController {

    private final FavoritesUseCase favorites;

    ListFavoritesController(FavoritesUseCase favorites) {
        this.favorites = favorites;
    }

    @GetMapping("/api/favorites")
    List<LibraryItem> mine(@AuthenticationPrincipal Jwt jwt) {
        return favorites.list(UUID.fromString(jwt.getSubject()));
    }
}
