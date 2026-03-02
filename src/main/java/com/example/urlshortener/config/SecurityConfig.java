package com.example.urlshortener.config;

import com.example.urlshortener.util.JwtUtil;
import com.example.urlshortener.service.UserService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {


    @Value("${app.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
    private String allowedOriginsProp;


    @Bean
    public FilterRegistrationBean<RateLimitingFilter> rateLimitingFilterRegistration(RateLimitingFilter filter) {
        FilterRegistrationBean<RateLimitingFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(-101); // Ensure RateLimiter runs before SecurityFilterChain
        return registration;
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtUtil jwtUtil, UserService userService) {
        return new JwtAuthFilter(jwtUtil, userService);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        // Custom JWT filter
        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        http
            .cors(cors -> cors.configurationSource(request -> {
                var config = new org.springframework.web.cors.CorsConfiguration();
                var origins = java.util.Arrays.stream(allowedOriginsProp.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
                config.setAllowedOrigins(origins);
                config.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                config.setAllowedHeaders(java.util.List.of("*"));
                config.setAllowCredentials(true);
                return config;
            }))
            .csrf(csrf -> csrf.disable())
            .headers(headers -> {
                headers.frameOptions(frame -> frame.deny());
                headers.referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                headers.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; img-src 'self' data: https:; script-src 'self'; style-src 'self' 'unsafe-inline'; connect-src 'self' https:; frame-ancestors 'none';"));
            })
            .authorizeHttpRequests(authz -> authz
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/logout").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.GET, "/health", "/health/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/{shortCode}").permitAll()
                .requestMatchers(HttpMethod.GET, "/analytics/{shortCode}").authenticated()
                .requestMatchers(HttpMethod.POST, "/shorten").authenticated()
                .anyRequest()
                .authenticated()
            );

        return http.build();
    }

    // JWT extraction and Spring Security context update
    public static class JwtAuthFilter implements jakarta.servlet.Filter {
        private final JwtUtil jwtUtil;
        private final UserService userService;

        public JwtAuthFilter(JwtUtil jwtUtil, UserService userService) {
            this.jwtUtil = jwtUtil;
            this.userService = userService;
        }

        @Override
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
                throws IOException, ServletException {
            HttpServletRequest request = (HttpServletRequest) servletRequest;
            String authHeader = request.getHeader("Authorization");
            String token = null;
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }
            if (token != null && jwtUtil.validateAccessToken(token)) {
                String username = jwtUtil.extractUsername(token);
                userService.findByUsername(username).ifPresent(user -> {
                    var authorities = java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"));
                    var auth = new UsernamePasswordAuthenticationToken(user, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                });
            }
            filterChain.doFilter(servletRequest, servletResponse);
        }
    }
}
