package com.example.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class ShortenRequest {

    @NotBlank(message = "originalUrl is required")
    @Size(max = 2048, message = "originalUrl is too long")
    private String originalUrl;

    @Size(min = 2, max = 32, message = "customShortCode length must be between 2 and 32")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "customShortCode may only contain letters, numbers, underscore, and hyphen")
    private String customShortCode;

    @Positive(message = "expiresInSeconds must be positive if provided")
    private Long expiresInSeconds;

    public ShortenRequest() {
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public String getCustomShortCode() {
        return customShortCode;
    }

    public void setCustomShortCode(String customShortCode) {
        this.customShortCode = customShortCode;
    }

    public Long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(Long expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }
}