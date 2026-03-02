package com.example.urlshortener.service;

import com.example.urlshortener.exception.InvalidUrlException;
import com.example.urlshortener.exception.UrlNotFoundException;
import com.example.urlshortener.exception.InvalidShortCodeException;
import com.example.urlshortener.exception.ShortCodeConflictException;
import com.example.urlshortener.util.ShortCodePolicy;
import com.example.urlshortener.model.UrlMapping;
import com.example.urlshortener.model.User;
import com.example.urlshortener.repository.UrlMappingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.example.urlshortener.repository.UrlMappingSummary;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class UrlShortenerService {

    private static final Logger log = LoggerFactory.getLogger(UrlShortenerService.class);

    private static final long DEFAULT_CACHE_TTL_SECONDS = 3600; // 1 hour

    @Autowired
    private UrlMappingRepository urlRepo;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private ShortCodePolicy shortCodePolicy;

    // Backward-compatible entrypoint (no expiration)
    public String shortenUrl(String originalUrl, User user) {
        return shortenUrl(originalUrl, user, null);
    }

    // New entrypoint with optional expiration (seconds from now)
    @Transactional
    public String shortenUrl(String originalUrl, User user, Long expiresInSeconds) {
        if (originalUrl == null || originalUrl.isBlank()) {
            throw new InvalidUrlException("URL cannot be empty");
        }
        if (!isValidUrl(originalUrl)) {
            throw new InvalidUrlException("Invalid URL format");
        }

        // Check cache for dedup (per user+url). Cache TTL is aligned with expiration, so stale returns should be rare.
        String cacheKey = String.format("url:%d:%s", user.getId(), originalUrl);
        try {
            log.info("CACHE GET key={}", cacheKey);
            String cachedShortCode = redis.opsForValue().get(cacheKey);
            if (cachedShortCode != null && !cachedShortCode.isBlank()) {
                log.info("CACHE HIT key={} valuePresent=true", cacheKey);
                return cachedShortCode;
            } else {
                log.info("CACHE MISS key={}", cacheKey);
            }
        } catch (Exception e) {
            log.warn("CACHE GET error key={} ex={}", cacheKey, e.toString());
        }

        // Check DB for existing active mapping (non-expired)
        Optional<UrlMapping> existing = urlRepo.findActiveByOriginalUrlAndUser(originalUrl, user);
        if (existing.isPresent() && existing.get().getShortCode() != null) {
            UrlMapping m = existing.get();
            // Write to Redis for future optimization with TTL aligned to expiration
            try {
                long ttl = cacheTtlSeconds(m.getExpiresAt());
                redis.opsForValue().set(cacheKey, m.getShortCode(), ttl, TimeUnit.SECONDS);
                log.info("CACHE SET key={} ttlSec={}", cacheKey, ttl);
                redis.opsForValue().set(m.getShortCode(), m.getOriginalUrl(), ttl, TimeUnit.SECONDS);
                log.info("CACHE SET key={} ttlSec={}", m.getShortCode(), ttl);
            } catch (Exception e) {
                log.warn("CACHE SET error for keys=[{},{}] ex={}", cacheKey, m.getShortCode(), e.toString());
            }
            return m.getShortCode();
        }

        // Otherwise create new mapping (single INSERT via SEQUENCE + @PrePersist for shortCode)
        UrlMapping mapping = new UrlMapping();
        mapping.setOriginalUrl(originalUrl);
        mapping.setUser(user);
        if (expiresInSeconds != null && expiresInSeconds > 0) {
            mapping.setExpiresAt(Instant.now().plusSeconds(expiresInSeconds));
        }

        try {
            mapping = urlRepo.save(mapping); // shortCode set by @PrePersist based on id
        } catch (DataIntegrityViolationException ex) {
            // Race: another request inserted the same (user, originalUrl) mapping
            Optional<UrlMapping> race = urlRepo.findActiveByOriginalUrlAndUser(originalUrl, user);
            if (race.isPresent()) {
                return race.get().getShortCode();
            }
            throw ex;
        }

        // Cache both lookup and reverse for future (TTL aligned to expiration)
        try {
            long ttl = cacheTtlSeconds(mapping.getExpiresAt());
            if (ttl > 0) {
                redis.opsForValue().set(mapping.getShortCode(), mapping.getOriginalUrl(), ttl, TimeUnit.SECONDS);
                redis.opsForValue().set(cacheKey, mapping.getShortCode(), ttl, TimeUnit.SECONDS);
            }
        } catch (Exception ignored) {}

        return mapping.getShortCode();
    }

    public String shortenUrlWithAlias(String originalUrl, User user, Long expiresInSeconds, String customShortCode) {
        if (originalUrl == null || originalUrl.isBlank()) {
            throw new InvalidUrlException("URL cannot be empty");
        }
        if (!isValidUrl(originalUrl)) {
            throw new InvalidUrlException("Invalid URL format");
        }

        // Dedup: if user already has an active mapping for this URL, return it (keeps existing behavior)
        Optional<UrlMapping> existing = urlRepo.findActiveByOriginalUrlAndUser(originalUrl, user);
        if (existing.isPresent() && existing.get().getShortCode() != null) {
            return existing.get().getShortCode();
        }

        // Validate alias
        if (customShortCode == null || customShortCode.isBlank()) {
            // Fallback to auto generation if alias not provided
            return shortenUrl(originalUrl, user, expiresInSeconds);
        }
        String alias = shortCodePolicy.normalize(customShortCode);
        try {
            shortCodePolicy.validateOrThrow(alias);
        } catch (IllegalArgumentException ex) {
            throw new InvalidShortCodeException(ex.getMessage(), ex);
        }
        if (shortCodePolicy.isReserved(alias)) {
            throw new InvalidShortCodeException("Alias is reserved");
        }
        if (urlRepo.existsByShortCode(alias)) {
            throw new ShortCodeConflictException("Alias already taken");
        }

        // Create mapping with explicit alias
        UrlMapping mapping = new UrlMapping();
        mapping.setOriginalUrl(originalUrl);
        mapping.setUser(user);
        mapping.setShortCode(alias);
        if (expiresInSeconds != null && expiresInSeconds > 0) {
            mapping.setExpiresAt(Instant.now().plusSeconds(expiresInSeconds));
        }

        try {
            mapping = urlRepo.save(mapping);
        } catch (DataIntegrityViolationException ex) {
            // Could be alias taken (race) or user-url dedup race; map accordingly
            if (urlRepo.existsByShortCode(alias)) {
                throw new ShortCodeConflictException("Alias already taken", ex);
            }
            Optional<UrlMapping> race = urlRepo.findActiveByOriginalUrlAndUser(originalUrl, user);
            if (race.isPresent()) {
                return race.get().getShortCode();
            }
            throw ex;
        }

        // Cache both lookup and reverse for future (TTL aligned to expiration)
        try {
            long ttl = cacheTtlSeconds(mapping.getExpiresAt());
            if (ttl > 0) {
                redis.opsForValue().set(mapping.getShortCode(), mapping.getOriginalUrl(), ttl, TimeUnit.SECONDS);
                String cacheKey = String.format("url:%d:%s", user.getId(), originalUrl);
                redis.opsForValue().set(cacheKey, mapping.getShortCode(), ttl, TimeUnit.SECONDS);
            }
        } catch (Exception ignored) {}

        return mapping.getShortCode();
    }

    private boolean isValidUrl(String url) {
        try {
            new java.net.URL(url).toURI();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Transactional
    public String getOriginalUrl(String shortCode) {
        // Read cache first for speed, but we still increment DB atomically
        String fromCache = null;
        try {
            log.info("CACHE GET key={}", shortCode);
            fromCache = redis.opsForValue().get(shortCode);
            if (fromCache != null) {
                log.info("CACHE HIT key={} valuePresent=true", shortCode);
            } else {
                log.info("CACHE MISS key={}", shortCode);
            }
        } catch (Exception e) {
            log.warn("CACHE GET error key={} ex={}", shortCode, e.toString());
        }

        // Atomically increment click count only if mapping is active
        int updated = urlRepo.incrementClickCount(shortCode);
        log.info("DB incrementClickCount shortCode={} updated={}", shortCode, updated);
        if (updated == 0) {
            throw new UrlNotFoundException("Short URL not found");
        }

        // Invalidate cached clickCount if present
        try {
            redis.delete(shortCode + ":clickCount");
        } catch (Exception ignored) {}

        if (fromCache != null) {
            return fromCache;
        }

        // Cache miss: load from DB with active check, cache with TTL aligned to expiration
        UrlMapping mapping = urlRepo.findActiveByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException("Short URL not found"));

        try {
            long ttl = cacheTtlSeconds(mapping.getExpiresAt());
            if (ttl > 0) {
                redis.opsForValue().set(shortCode, mapping.getOriginalUrl(), ttl, TimeUnit.SECONDS);
                log.info("CACHE SET key={} ttlSec={}", shortCode, ttl);
            }
        } catch (Exception e) {
            log.warn("CACHE SET error key={} ex={}", shortCode, e.toString());
        }

        return mapping.getOriginalUrl();
    }

    public Integer getClickCount(String shortCode) {
        // Try to get the click count from Redis cache first
        String clickCountFromCache = null;
        String clickKey = shortCode + ":clickCount";
        try {
            log.info("CACHE GET key={}", clickKey);
            clickCountFromCache = redis.opsForValue().get(clickKey);
            if (clickCountFromCache != null) {
                log.info("CACHE HIT key={} valuePresent=true", clickKey);
            } else {
                log.info("CACHE MISS key={}", clickKey);
            }
        } catch (Exception e) {
            log.warn("CACHE GET error key={} ex={}", clickKey, e.toString());
        }

        if (clickCountFromCache != null) {
            return Integer.parseInt(clickCountFromCache);
        }

        // If not in cache, fetch it from the database (use non-active finder to show historical if needed)
        Optional<UrlMapping> urlMappingOpt = urlRepo.findByShortCode(shortCode);
        if (urlMappingOpt.isPresent()) {
            UrlMapping urlMapping = urlMappingOpt.get();
            try {
                String setClickKey = shortCode + ":clickCount";
                redis.opsForValue().set(setClickKey, String.valueOf(urlMapping.getClickCount()), 1, TimeUnit.HOURS);
                log.info("CACHE SET key={} ttlSec={}", setClickKey, 3600);
            } catch (Exception e) {
                log.warn("CACHE SET error key={} ex={}", shortCode + ":clickCount", e.toString());
            }
            return urlMapping.getClickCount();
        } else {
            throw new UrlNotFoundException("Short URL not found: " + shortCode);
        }
    }

    public Integer getClickCountIfOwner(String shortCode, User user) {
        Optional<UrlMapping> urlMappingOpt = urlRepo.findByShortCode(shortCode);
        if (urlMappingOpt.isPresent()) {
            UrlMapping urlMapping = urlMappingOpt.get();
            if (urlMapping.getUser() != null && urlMapping.getUser().getId().equals(user.getId())) {
                return getClickCount(shortCode);
            }
        }
        return null;
    }

    public Page<UrlMappingSummary> listMyLinks(User user, boolean includeExpired, Pageable pageable) {
        return urlRepo.findSummariesByUser(user, includeExpired, pageable);
    }

    private long cacheTtlSeconds(Instant expiresAt) {
        if (expiresAt == null) {
            return DEFAULT_CACHE_TTL_SECONDS;
        }
        long secondsLeft = Duration.between(Instant.now(), expiresAt).getSeconds();
        if (secondsLeft <= 0) {
            return 0;
        }
        return Math.max(1, Math.min(DEFAULT_CACHE_TTL_SECONDS, secondsLeft));
    }
}
