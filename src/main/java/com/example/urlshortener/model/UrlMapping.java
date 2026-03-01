package com.example.urlshortener.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_original", columnNames = {"user_id", "original_url"})
        },
        indexes = {
                @Index(name = "idx_short_code", columnList = "short_code"),
                @Index(name = "idx_expires_at", columnList = "expires_at"),
                @Index(name = "idx_user_created_at", columnList = "user_id, created_at")
        }
)
public class UrlMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "url_mapping_seq")
    @SequenceGenerator(name = "url_mapping_seq", sequenceName = "url_mapping_seq", allocationSize = 50)
    private Long id;

    @Column(name = "short_code", unique = true)
    private String shortCode;

    @Column(name = "original_url", nullable = false)
    private String originalUrl;

    @Column(name = "click_count")
    private int clickCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    public UrlMapping() {}

    public UrlMapping(String shortCode, String originalUrl) {
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
    }

    @PrePersist
    private void prePersist() {
        if (this.shortCode == null && this.id != null) {
            this.shortCode = com.example.urlshortener.util.Base62Encoder.encode(this.id);
        }
    }

    public Long getId() {
        return id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public int getClickCount() {
        return clickCount;
    }

    public User getUser() {
        return user;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public void setClickCount(int clickCount) {
        this.clickCount = clickCount;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
