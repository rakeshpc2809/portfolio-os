package com.portfolioos.core.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class SecurityInterceptor implements HandlerInterceptor {
    private final String configuredToken;

    public SecurityInterceptor(@Value("${api.auth.token:${API_AUTH_TOKEN:}}") String tokenProp) {
        this(tokenProp, System.getenv("API_AUTH_TOKEN"));
    }

    public SecurityInterceptor() {
        this(null, System.getenv("API_AUTH_TOKEN"));
    }

    SecurityInterceptor(String tokenProp, String envTokenOverride) {
        String resolved = (tokenProp != null && !tokenProp.isBlank()) ? tokenProp : envTokenOverride;
        if (resolved == null || resolved.trim().isEmpty()) {
            throw new IllegalStateException("SECURITY CRITICAL: API_AUTH_TOKEN environment variable is required at startup and cannot be empty.");
        }
        this.configuredToken = resolved.trim();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String providedToken = request.getHeader("X-Api-Auth-Token");
        if (providedToken == null || providedToken.trim().isEmpty()) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                providedToken = authHeader.substring(7);
            }
        }
        if (providedToken == null || providedToken.trim().isEmpty()) {
            providedToken = request.getParameter("token");
        }
        boolean isValid = false;
        if (providedToken != null && !providedToken.trim().isEmpty()) {
            isValid = MessageDigest.isEqual(
                configuredToken.getBytes(StandardCharsets.UTF_8),
                providedToken.trim().getBytes(StandardCharsets.UTF_8)
            );
        }
        if (!isValid) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Unauthorized: Missing or invalid X-Api-Auth-Token header or token parameter.\"}");
            return false;
        }
        return true;
    }
}
