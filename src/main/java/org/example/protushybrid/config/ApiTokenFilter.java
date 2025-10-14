package org.example.protushybrid.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ApiTokenFilter extends OncePerRequestFilter {

    @Value("${api.token}")
    private String apiToken;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Skip filter for health check endpoints
        String path = request.getRequestURI();
        if (path.startsWith("/actuator/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Check for API token in header
        String providedToken = request.getHeader("X-API-Token");

        if (providedToken == null || !providedToken.equals(apiToken)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Invalid or missing API token\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
