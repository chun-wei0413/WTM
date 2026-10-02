package com.wtm.adapter.in.web;

import com.wtm.application.library.FavoritesHandler;
import com.wtm.application.library.LibraryItem;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in user's own favorites.
 */
@RestController
@RequestMapping("/api/favorites")
class FavoriteController {

    private final FavoritesHandler favorites;

    FavoriteController(FavoritesHandler favorites) {
        this.favorites = favorites;
    }

    @GetMapping
    List<LibraryItem> mine(@AuthenticationPrincipal Jwt jwt) {
        return favorites.list(UUID.fromString(jwt.getSubject()));
    }

    @PutMapping("/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void add(@PathVariable UUID templateId, @AuthenticationPrincipal Jwt jwt) {
        favorites.add(UUID.fromString(jwt.getSubject()), templateId);
    }

    @DeleteMapping("/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable UUID templateId, @AuthenticationPrincipal Jwt jwt) {
        favorites.remove(UUID.fromString(jwt.getSubject()), templateId);
    }
}
