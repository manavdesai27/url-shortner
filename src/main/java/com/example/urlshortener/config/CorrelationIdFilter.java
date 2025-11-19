package com.example.urlshortener.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Populates SLF4J MDC with correlation fields so all subsequent logs (including Hibernate/Redis driver logs)
 * carry the same request context for easy correlation:
 * - requestId: from X-Request-ID or generated
 * - user: authenticated username if available
 * - method, path, clientIp
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_USER = "user";
    public static final String MDC_METHOD = "method";
    public static final String MDC_PATH = "path";
    public static final String MDC_CLIENT_IP = "clientIp";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String requestId = Optional.ofNullable(request.getHeader("X-Request-ID"))
                .filter(v -> !v.isBlank())
                .orElse(UUID.randomUUID().toString());

        String clientIp = getClientIP(request);
        String method = request.getMethod();
        String path = request.getRequestURI();

        // Try to capture authenticated principal (if any)
        String user = null;
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getName() != null) {
                user = auth.getName();
            }
        } catch (Exception ignored) {}

        MDC.put(MDC_REQUEST_ID, requestId);
        MDC.put(MDC_METHOD, method);
        MDC.put(MDC_PATH, path);
        MDC.put(MDC_CLIENT_IP, clientIp);
        if (user != null) {
            MDC.put(MDC_USER, user);
        }

        try {
            // Also echo request id back so downstream services can propagate it
            response.setHeader("X-Request-ID", requestId);
            chain.doFilter(request, response);
        } finally {
            // Always clear to avoid MDC leakage
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_METHOD);
            MDC.remove(MDC_PATH);
            MDC.remove(MDC_CLIENT_IP);
            MDC.remove(MDC_USER);
        }
    }

    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isBlank()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
