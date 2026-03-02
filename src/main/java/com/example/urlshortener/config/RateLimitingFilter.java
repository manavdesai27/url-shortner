package com.example.urlshortener.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.distributed.proxy.ProxyManager;

import java.io.IOException;
import java.time.Duration;
import java.util.function.Supplier;
import java.nio.charset.StandardCharsets;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    // ProxyManager<byte[]> is provided via RedisConfig.
    @Autowired
    private ProxyManager<byte[]> proxyManager;

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

    private final Bandwidth refreshLimit = Bandwidth.builder()
            .capacity(30)
            .refillIntervally(30, Duration.ofMinutes(1))
            .build();

    private final Bandwidth analyticsLimit = Bandwidth.builder()
            .capacity(60)
            .refillIntervally(60, Duration.ofMinutes(1))
            .build();

    private final Bandwidth linksLimit = Bandwidth.builder()
            .capacity(60)
            .refillIntervally(60, Duration.ofMinutes(1))
            .build();

    // ProxyManager<byte[]> is injected and provided by configuration; no manual setup or cleanup needed.

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        return !(
            ("/auth/login".equals(path) && "POST".equals(method)) ||
            ("/auth/register".equals(path) && "POST".equals(method)) ||
            ("/auth/refresh".equals(path) && "POST".equals(method)) ||
            ("/shorten".equals(path) && "POST".equals(method)) ||
            (path.matches("^/analytics/[A-Za-z0-9_-]{2,32}$") && "GET".equals(method)) ||
            ("/links".equals(path) && "GET".equals(method)) ||
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

        String bucketKey;
        Supplier<io.github.bucket4j.BucketConfiguration> configSupplier;

        if ("/auth/login".equals(path) && "POST".equals(method)) {
            bucketKey = "LOGIN:" + clientIp;
            configSupplier = () -> io.github.bucket4j.BucketConfiguration.builder().addLimit(loginLimit).build();
        } else if ("/auth/register".equals(path) && "POST".equals(method)) {
            bucketKey = "REGISTER:" + clientIp;
            configSupplier = () -> io.github.bucket4j.BucketConfiguration.builder().addLimit(registerLimit).build();
        } else if ("/shorten".equals(path) && "POST".equals(method)) {
            bucketKey = "SHORTEN:" + clientIp;
            configSupplier = () -> io.github.bucket4j.BucketConfiguration.builder().addLimit(shortenLimit).build();
        } else if (path.matches("^/[a-zA-Z0-9]+$") && "GET".equals(method)) {
            bucketKey = "REDIRECT:" + clientIp;
            configSupplier = () -> io.github.bucket4j.BucketConfiguration.builder().addLimit(redirectLimit).build();
        } else if ("/auth/refresh".equals(path) && "POST".equals(method)) {
            bucketKey = "REFRESH:" + clientIp;
            configSupplier = () -> io.github.bucket4j.BucketConfiguration.builder().addLimit(refreshLimit).build();
        } else if (path.matches("^/analytics/[A-Za-z0-9_-]{2,32}$") && "GET".equals(method)) {
            bucketKey = "ANALYTICS:" + clientIp;
            configSupplier = () -> io.github.bucket4j.BucketConfiguration.builder().addLimit(analyticsLimit).build();
        } else if ("/links".equals(path) && "GET".equals(method)) {
            bucketKey = "LINKS:" + clientIp;
            configSupplier = () -> io.github.bucket4j.BucketConfiguration.builder().addLimit(linksLimit).build();
        } else {
            filterChain.doFilter(request, response);
            return;
        }

        Bucket bucket = proxyManager.builder().build(bucketKey.getBytes(StandardCharsets.UTF_8), configSupplier);

        if (!bucket.tryConsume(1)) {
            log.warn("RATE_LIMITED method={} path={} clientIp={} bucketKey={}", method, path, clientIp, bucketKey);
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
