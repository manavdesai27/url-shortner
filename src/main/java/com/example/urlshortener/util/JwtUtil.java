package com.example.urlshortener.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    private static final String CLAIM_TYP = "typ";
    private static final String TYP_ACCESS = "access";
    private static final String TYP_REFRESH = "refresh";

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.access-expiration-ms:900000}") // 15 minutes default
    private long accessExpirationMs;

    @Value("${app.jwt.refresh-expiration-ms:1209600000}") // 14 days default
    private long refreshExpirationMs;

    private Key getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // ===== Token generation =====

    public String generateAccessToken(String username) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + accessExpirationMs))
                .claim(CLAIM_TYP, TYP_ACCESS)
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(String username) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setSubject(username)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + refreshExpirationMs))
                .claim(CLAIM_TYP, TYP_REFRESH)
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // ===== Token parsing / validation =====

    public String extractUsername(String token) {
        return getClaims(token).getSubject();
    }

    public String extractType(String token) {
        Object t = getClaims(token).get(CLAIM_TYP);
        return t != null ? t.toString() : null;
    }

    public boolean validateTokenOfType(String token, String expectedType) {
        try {
            Claims claims = getClaims(token);
            String typ = claims.get(CLAIM_TYP, String.class);
            if (typ == null || !typ.equals(expectedType)) {
                return false;
            }
            // Signature and expiration are already validated by parseClaimsJws
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean validateAccessToken(String token) {
        return validateTokenOfType(token, TYP_ACCESS);
    }

    public boolean validateRefreshToken(String token) {
        return validateTokenOfType(token, TYP_REFRESH);
    }

    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getKey())
                .setAllowedClockSkewSeconds(60)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String extractJti(String token) {
        return getClaims(token).getId();
    }

    public Date extractExpiration(String token) {
        return getClaims(token).getExpiration();
    }
}