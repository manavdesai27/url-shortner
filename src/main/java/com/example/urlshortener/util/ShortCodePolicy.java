package com.example.urlshortener.util;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Centralized policy for validating and normalizing custom short codes.
 * - Normalization: trim + lowercase
 * - Allowed: a-z, 0-9, '-' and '_'
 * - Length: 2 to 32 characters
 * - Global reserved words are disallowed
 */
@Component
public class ShortCodePolicy {

    // 2-32 chars, start with [a-z0-9], remaining may include [-_]
    private static final Pattern ALIAS_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-_]{1,31}$");

    private static final Set<String> RESERVED;
    static {
        Set<String> r = new HashSet<>();
        // App routes and common words to prevent collisions and confusion
        Collections.addAll(r,
                "api", "login", "logout", "signup", "register",
                "links", "analytics", "stats", "health", "actuator",
                "shorten", "admin", "user", "users",
                "l" // commonly used short path prefix
        );
        RESERVED = Collections.unmodifiableSet(r);
    }

    public String normalize(String alias) {
        if (alias == null) return null;
        return alias.trim().toLowerCase();
    }

    public void validateOrThrow(String alias) {
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("Alias cannot be empty");
        }
        if (!ALIAS_PATTERN.matcher(alias).matches()) {
            throw new IllegalArgumentException("Alias must be 2-32 chars: a-z, 0-9, '-' or '_'");
        }
    }

    public boolean isReserved(String alias) {
        if (alias == null) return false;
        return RESERVED.contains(alias);
    }

    public Set<String> reservedWords() {
        return RESERVED;
    }
}