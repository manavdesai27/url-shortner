package com.example.urlshortener.controller;

import com.example.urlshortener.service.UrlShortenerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import com.example.urlshortener.model.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/")
public class UrlShortenerController {

    @Autowired
    private UrlShortenerService service;

    @PostMapping("/shorten")
    public ResponseEntity<String> shortenUrl(@RequestBody Map<String, String> body,
                                             @AuthenticationPrincipal User user) {
        String originalUrl = body.get("originalUrl");
        System.out.println("Original URL: " + originalUrl);
        String shortCode = service.shortenUrl(originalUrl, user);
        return ResponseEntity.ok(shortCode);
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<?> redirectToOriginal(@PathVariable String shortCode) {
        String originalUrl = service.getOriginalUrl(shortCode);
        if (originalUrl != null) {
            return ResponseEntity.status(302) // 302 Found → redirect
                    .header("Location", originalUrl)
                    .build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    @GetMapping("/analytics/{shortCode}")
    public ResponseEntity<?> getClickCount(@PathVariable String shortCode,
                                           @AuthenticationPrincipal User user) {
        Integer clickCount = service.getClickCountIfOwner(shortCode, user);

        if (clickCount != null) {
            return ResponseEntity.ok().body(clickCount);  // Directly return the click count value
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Short URL not found or not yours");
        }
    }
}
