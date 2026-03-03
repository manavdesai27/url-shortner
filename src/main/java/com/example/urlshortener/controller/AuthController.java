package com.example.urlshortener.controller;

import com.example.urlshortener.service.UserService;
import com.example.urlshortener.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import jakarta.validation.Valid;
import org.springframework.data.redis.core.StringRedisTemplate;
import jakarta.servlet.http.HttpServletRequest;
import com.example.urlshortener.dto.LoginRequest;
import com.example.urlshortener.dto.RegisterRequest;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import java.security.Principal;
import java.util.Map;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${app.cookie.same-site:Lax}")
    private String cookieSameSite;

    @Value("${app.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
    private String allowedOriginsProp;

    @Autowired
    private StringRedisTemplate redis;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest body) {
        String username = body.getUsername() != null ? body.getUsername().trim() : null;
        String password = body.getPassword();
        if (username == null || password == null) {
            return ResponseEntity.badRequest().body("Username and password required.");
        }
        try {
            userService.registerUser(username, password);
            return ResponseEntity.ok("Registered");
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        if (!isTrustedOrigin(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Untrusted origin"));
        }
        String username = body.getUsername();
        String password = body.getPassword();
        return userService.authenticate(username, password)
                .map(user -> {
                    String accessToken = jwtUtil.generateAccessToken(user.getUsername());
                    String refreshToken = jwtUtil.generateRefreshToken(user.getUsername());
                    String jti = jwtUtil.extractJti(refreshToken);
                    long ttlSec = Math.max(1L, (jwtUtil.extractExpiration(refreshToken).getTime() - System.currentTimeMillis()) / 1000L);
                    try {
                        redis.opsForValue().set("rt:" + user.getUsername(), jti, ttlSec, TimeUnit.SECONDS);
                    } catch (Exception ignored) {}
                    ResponseCookie cookie = ResponseCookie.from("__Host-refresh", refreshToken)
                            .httpOnly(true)
                            .secure(cookieSecure)
                            .sameSite(cookieSameSite)
                            .path("/")
                            .maxAge(ttlSec)
                            .build();
                    return ResponseEntity.ok()
                            .header(HttpHeaders.SET_COOKIE, cookie.toString())
                            .body(Map.of("accessToken", accessToken, "username", user.getUsername()));
                })
                .orElse(ResponseEntity.status(401).body(Map.of("error", "Invalid username or password")));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@CookieValue(name="__Host-refresh", required=false) String refreshToken,
                                    HttpServletRequest request) {
        if (!isTrustedOrigin(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Untrusted origin"));
        }
        try {
            if (refreshToken != null && jwtUtil.validateRefreshToken(refreshToken)) {
                String username = jwtUtil.extractUsername(refreshToken);
                redis.delete("rt:" + username);
            }
        } catch (Exception ignored) {}

        ResponseCookie deleteCookie = ResponseCookie.from("__Host-refresh", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(0)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .body("Logged out");
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(name="__Host-refresh", required=false) String refreshToken,
                                     HttpServletRequest request) {
        if (!isTrustedOrigin(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Untrusted origin"));
        }
        if (refreshToken == null || !jwtUtil.validateRefreshToken(refreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid refresh token"));
        }
        String username = jwtUtil.extractUsername(refreshToken);
        String jti = jwtUtil.extractJti(refreshToken);
        String key = "rt:" + username;
        String stored = null;
        try {
            stored = redis.opsForValue().get(key);
        } catch (Exception ignored) {}

        if (stored == null || !stored.equals(jti)) {
            try { redis.delete(key); } catch (Exception ignored) {}
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid refresh token"));
        }

        String newAccess = jwtUtil.generateAccessToken(username);
        String newRefresh = jwtUtil.generateRefreshToken(username);
        String newJti = jwtUtil.extractJti(newRefresh);
        long ttlSec = Math.max(1L, (jwtUtil.extractExpiration(newRefresh).getTime() - System.currentTimeMillis()) / 1000L);

        try {
            redis.opsForValue().set(key, newJti, ttlSec, TimeUnit.SECONDS);
        } catch (Exception ignored) {}

        ResponseCookie cookie = ResponseCookie.from("__Host-refresh", newRefresh)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(ttlSec)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(Map.of("accessToken", newAccess, "username", username));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthenticated"));
        }

        Object principal = auth.getPrincipal();
        String username;

        if (principal instanceof UserDetails) {
            username = ((UserDetails) principal).getUsername();
        } else if (principal instanceof Principal) {           // java.security.Principal
            username = ((Principal) principal).getName();
        } else {
            if (principal instanceof com.example.urlshortener.model.User) {
                username = ((com.example.urlshortener.model.User) principal).getUsername();
            } else {
                username = String.valueOf(principal);
            }
        }

        return ResponseEntity.ok(Map.of("username", username));
    }

    private boolean isTrustedOrigin(HttpServletRequest request) {
        try {
            String origin = request.getHeader("Origin");
            String referer = request.getHeader("Referer");
            var allowed = java.util.Arrays.stream(allowedOriginsProp.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            if (origin != null && !origin.isBlank()) {
                return allowed.contains(origin);
            }
            if (referer != null && !referer.isBlank()) {
                for (String a : allowed) {
                    if (!a.isEmpty() && referer.startsWith(a)) {
                        return true;
                    }
                }
                return false;
            }
        } catch (Exception ignored) {}
        // If no headers, treat as trusted (e.g., same-origin or non-browser clients)
        return true;
    }
}