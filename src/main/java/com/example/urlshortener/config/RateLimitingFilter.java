package com.example.urlshortener.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bandwidth;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> registerBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> shortenBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> redirectBuckets = new ConcurrentHashMap<>();

    private final Bandwidth loginLimit = Bandwidth.builder()
            .capacity(10)
            .refillIntervally(10, Duration.ofMinutes(1))
            .build();
    private final Bandwidth registerLimit = Bandwidth.builder()
            .capacity(5)
            .refillIntervally(5, Duration.ofMinutes(1))
            .build();
    private final Bandwidth shortenLimit = Bandwidth.builder()
            .capacity(20)
            .refillIntervally(20, Duration.ofMinutes(1))
            .build();
    private final Bandwidth redirectLimit = Bandwidth.builder()
            .capacity(100)
            .refillIntervally(100, Duration.ofMinutes(1))
            .build();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        return !(
            ("/auth/login".equals(path) && "POST".equals(method)) ||
            ("/auth/register".equals(path) && "POST".equals(method)) ||
            ("/shorten".equals(path) && "POST".equals(method)) ||
            (path.matches("^/[a-zA-Z0-9]+$") && "GET".equals(method))
        );
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, 
            HttpServletResponse response, 
            FilterChain filterChain
    ) throws ServletException, IOException {
        String clientIp = getClientIP(request);
        String path = request.getRequestURI();
        String method = request.getMethod();
        Bucket bucket;

        if ("/auth/login".equals(path) && "POST".equals(method)) {
            bucket = loginBuckets.computeIfAbsent(clientIp, k -> Bucket.builder().addLimit(loginLimit).build());
        } else if ("/auth/register".equals(path) && "POST".equals(method)) {
            bucket = registerBuckets.computeIfAbsent(clientIp, k -> Bucket.builder().addLimit(registerLimit).build());
        } else if ("/shorten".equals(path) && "POST".equals(method)) {
            bucket = shortenBuckets.computeIfAbsent(clientIp, k -> Bucket.builder().addLimit(shortenLimit).build());
        } else if (path.matches("^/[a-zA-Z0-9]+$") && "GET".equals(method)) {
            bucket = redirectBuckets.computeIfAbsent(clientIp, k -> Bucket.builder().addLimit(redirectLimit).build());
        } else {
            filterChain.doFilter(request, response);
            return;
        }

        if (!bucket.tryConsume(1)) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\": \"Too Many Requests. Please slow down.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
