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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserService userService;

    @Value("${app.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
    private String allowedOriginsProp;

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public FilterRegistrationBean<RateLimitingFilter> rateLimitingFilterRegistration(RateLimitingFilter filter) {
        FilterRegistrationBean<RateLimitingFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(-101); // Ensure RateLimiter runs before SecurityFilterChain
        return registration;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Custom JWT filter
        http.addFilterBefore(new JwtAuthFilter(jwtUtil, userService), UsernamePasswordAuthenticationFilter.class);

        http
            .cors(cors -> cors.configurationSource(request -> {
                var config = new org.springframework.web.cors.CorsConfiguration();
                var origins = java.util.Arrays.stream(allowedOriginsProp.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
                config.setAllowedOriginPatterns(origins);
                config.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                config.setAllowedHeaders(java.util.List.of("*"));
                config.setAllowCredentials(true);
                return config;
            }))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authz -> authz
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/logout").permitAll()
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
            String token = null;
            if (request.getCookies() != null) {
                for (Cookie cookie : request.getCookies()) {
                    if ("token".equals(cookie.getName())) {
                        token = cookie.getValue();
                        break;
                    }
                }
            }
            if (token != null && jwtUtil.validateToken(token)) {
                String username = jwtUtil.extractUsername(token);
                userService.findByUsername(username).ifPresent(user -> {
                    var auth = new UsernamePasswordAuthenticationToken(
                            user, null, null); // No roles/authorities
                    SecurityContextHolder.getContext().setAuthentication(auth);
                });
            }
            filterChain.doFilter(servletRequest, servletResponse);
        }
    }
}
