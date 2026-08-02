package com.portfolioos.core.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class SecurityInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = System.getenv("API_AUTH_TOKEN");
        if (token == null || token.trim().isEmpty()) {
            return true;
        }

        String clientHeader = request.getHeader("X-Api-Auth-Token");
        if (clientHeader == null) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                clientHeader = authHeader.substring(7);
            }
        }

        if (clientHeader == null) {
            clientHeader = request.getParameter("token");
        }

        if (!token.equals(clientHeader)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Unauthorized: Missing or invalid X-Api-Auth-Token header or token parameter.\"}");
            return false;
        }

        return true;
    }
}
