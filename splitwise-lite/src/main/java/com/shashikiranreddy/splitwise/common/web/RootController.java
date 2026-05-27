package com.shashikiranreddy.splitwise.common.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Friendly landing endpoint. Lets a curious visitor see what the API is and
 * how to authenticate, without hitting the Spring Security wall.
 */
@RestController
public class RootController {

    @GetMapping("/")
    public Map<String, Object> root() {
        return Map.of(
                "name", "Splitwise-Lite",
                "version", "0.1.0",
                "status", "UP",
                "description", "REST API for tracking shared group expenses with debt-simplification.",
                "docs", "/swagger-ui.html",
                "github", "https://github.com/shashi1349/kiro/tree/main/splitwise-lite",
                "publicEndpoints", List.of("/", "/swagger-ui.html", "/auth/register", "/auth/login", "/actuator/health"),
                "auth", "Send 'Authorization: Bearer <jwt>' on protected routes; obtain a JWT via POST /auth/login"
        );
    }
}
