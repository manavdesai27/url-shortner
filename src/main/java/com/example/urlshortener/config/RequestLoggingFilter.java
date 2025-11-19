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
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Logs request start/end with timing, correlating via MDC (requestId, user, etc.).
 * Runs right after CorrelationIdFilter so MDC is present for all logs in the flow.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        long start = System.currentTimeMillis();
        String method = request.getMethod();
        String path = request.getRequestURI();
        String requestId = MDC.get("requestId");
        String user = MDC.get("user");
        String clientIp = MDC.get("clientIp");

        log.info("REQ START method={} path={} clientIp={} user={} requestId={}",
                method, path, clientIp, user, requestId);

        try {
            chain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - start;
            int status = response.getStatus();
            log.info("REQ END   method={} path={} status={} durationMs={} requestId={}",
                    method, path, status, duration, requestId);
        }
    }
}
