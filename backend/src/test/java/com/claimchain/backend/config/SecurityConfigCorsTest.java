package com.claimchain.backend.config;

import com.claimchain.backend.security.RestAccessDeniedHandler;
import com.claimchain.backend.security.RestAuthenticationEntryPoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigCorsTest {

    @Test
    void corsConfiguration_allowsExpectedOriginsMethodsAndHeaders() {
        CorsProperties corsProperties = new CorsProperties();
        corsProperties.setAllowedOrigins(List.of("https://app.claimchain.example"));

        SecurityConfig securityConfig = new SecurityConfig(
                corsProperties,
                new RequestIdFilter(),
                new RestAuthenticationEntryPoint(new ObjectMapper()),
                new RestAccessDeniedHandler(new ObjectMapper())
        );

        CorsConfigurationSource source = securityConfig.corsConfigurationSource();

        MockHttpServletRequest preflightRequest = new MockHttpServletRequest("OPTIONS", "/api/claims");
        preflightRequest.addHeader("Origin", "http://localhost:3000");
        preflightRequest.addHeader("Access-Control-Request-Method", "POST");

        CorsConfiguration corsConfig = source.getCorsConfiguration(preflightRequest);

        assertThat(corsConfig).isNotNull();
        assertThat(corsConfig.getAllowedOrigins()).contains(
                "http://localhost:3000",
                "http://127.0.0.1:3000",
                "https://app.claimchain.example"
        );
        assertThat(corsConfig.getAllowedMethods()).contains("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(corsConfig.getAllowedHeaders()).contains("Authorization", "Content-Type", "Idempotency-Key");
        assertThat(corsConfig.getAllowCredentials()).isFalse();
        assertThat(corsConfig.checkOrigin("https://not-allowed.example")).isNull();
    }
}
