package com.example.urlshortener.controller;

import com.example.urlshortener.service.UrlShortenerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import com.example.urlshortener.repository.UrlMappingSummary;

import java.util.Map;
import com.example.urlshortener.model.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/")
public class UrlShortenerController {

    @Autowired
    private UrlShortenerService service;

    @PostMapping("/shorten")
    public ResponseEntity<?> shortenUrl(@RequestBody Map<String, String> body,
                                             @AuthenticationPrincipal User user) {
        String originalUrl = body.get("originalUrl");

        // Optional expiration support (seconds from now)
        Long expiresInSeconds = null;
        String expiresStr = body.get("expiresInSeconds");
        if (expiresStr != null && !expiresStr.isBlank()) {
            try {
                long val = Long.parseLong(expiresStr);
                if (val > 0) {
                    expiresInSeconds = val;
                }
            } catch (NumberFormatException ignored) {}
        }

        String shortCode;
        try {
            if (expiresInSeconds != null) {
                shortCode = service.shortenUrl(originalUrl, user, expiresInSeconds);
            } else {
                // Backward compatible path
                shortCode = service.shortenUrl(originalUrl, user);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        return ResponseEntity.ok(Map.of("shortCode", shortCode, "originalUrl", originalUrl));
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<?> redirectToOriginal(@PathVariable String shortCode) {
        try {
            String originalUrl = service.getOriginalUrl(shortCode);
            return ResponseEntity.status(302)
                    .header("Location", originalUrl)
                    .build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/analytics/{shortCode}")
    public ResponseEntity<?> getClickCount(@PathVariable String shortCode,
                                           @AuthenticationPrincipal User user) {
        try {
            Integer clickCount = service.getClickCountIfOwner(shortCode, user);
            if (clickCount != null) {
                return ResponseEntity.ok(Map.of("shortCode", shortCode, "clickCount", clickCount));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Short URL not found or not yours"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/links")
    public ResponseEntity<?> getMyLinks(
            @AuthenticationPrincipal User user,
            @RequestParam(name = "includeExpired", defaultValue = "false") boolean includeExpired,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<UrlMappingSummary> page = service.listMyLinks(user, includeExpired, pageable);
        return ResponseEntity.ok(page);
    }
}
