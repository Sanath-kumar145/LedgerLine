package com.ledgerline.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("HealthController Unit Tests")
class HealthControllerTest {

    private final HealthController healthController = new HealthController();

    @Test
    @DisplayName("Health endpoint returns status UP and service name")
    void testHealthCheck() {
        ResponseEntity<Map<String, Object>> response = healthController.healthCheck();

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("UP", response.getBody().get("status"));
        assertEquals("ledgerline-api", response.getBody().get("service"));
        assertEquals("1.0.0", response.getBody().get("version"));
        assertNotNull(response.getBody().get("timestamp"));
    }
}

