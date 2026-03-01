package com.example.urlshortener.repository;

import java.time.Instant;

public interface UrlMappingSummary {
    String getShortCode();
    String getOriginalUrl();
    int getClickCount();
    Instant getCreatedAt();
    Instant getExpiresAt();
}