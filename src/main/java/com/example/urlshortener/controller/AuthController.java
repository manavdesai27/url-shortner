package com.example.urlshortener.controller;

import com.example.urlshortener.service.UserService;
import com.example.urlshortener.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
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

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
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
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || password == null) {
            return ResponseEntity.badRequest().body("Username and password required.");
        }
        return userService.authenticate(username, password)
                .map(user -> {
                    String accessToken = jwtUtil.generateAccessToken(user.getUsername());
                    String refreshToken = jwtUtil.generateRefreshToken(user.getUsername());
                    // __Host- cookie: Secure + Path=/ + no Domain
                    ResponseCookie cookie = ResponseCookie.from("__Host-refresh", refreshToken)
                            .httpOnly(true)
                            .secure(cookieSecure)
                            .sameSite(cookieSameSite)
                            .path("/")
                            .maxAge(60L * 60L * 24L * 14L)
                            .build();
                    return ResponseEntity.ok()
                            .header(HttpHeaders.SET_COOKIE, cookie.toString())
                            .body(Map.of("accessToken", accessToken, "username", user.getUsername()));
                })
                .orElse(ResponseEntity.status(401).body(Map.of("error", "Invalid username or password")));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
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

    // @GetMapping("/me")
    // public ResponseEntity<?> me() {
    //     var auth = SecurityContextHolder.getContext().getAuthentication();
    //     if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
    //         return ResponseEntity.status(401).body("Unauthenticated");
    //     }
    //     return ResponseEntity.ok(Map.of("username", auth.getName()));
    // }
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(name="__Host-refresh", required=false) String refreshToken) {
        if (refreshToken == null || !jwtUtil.validateRefreshToken(refreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid refresh token"));
        }
        String username = jwtUtil.extractUsername(refreshToken);
        String newAccess = jwtUtil.generateAccessToken(username);
        String newRefresh = jwtUtil.generateRefreshToken(username);

        ResponseCookie cookie = ResponseCookie.from("__Host-refresh", newRefresh)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(60L * 60L * 24L * 14L)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(Map.of("accessToken", newAccess));
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
            // Fallback: if it's your custom user entity, cast and extract the field
            if (principal instanceof com.example.urlshortener.model.User) {
                username = ((com.example.urlshortener.model.User) principal).getUsername(); // or getEmail()
            } else {
                username = String.valueOf(principal); // fallback to toString()
            }
        }

        return ResponseEntity.ok(Map.of("username", username));
    }
}
