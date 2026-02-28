package com.claimchain.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String ATTRIBUTE_NAME = "requestId";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String incoming = request.getHeader(HEADER_NAME);
        String requestId = normalize(incoming);

        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }

        request.setAttribute(ATTRIBUTE_NAME, requestId);
        MDC.put(MDC_KEY, requestId);

        try {
            // helpful for clients/debugging (optional but safe)
            response.setHeader(HEADER_NAME, requestId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        // Limit length to prevent log/headers abuse
        if (trimmed.length() > 64) trimmed = trimmed.substring(0, 64);
        // Keep only safe characters
        if (!trimmed.matches("[A-Za-z0-9._\\-]+")) return null;
        return trimmed;
    }
}
