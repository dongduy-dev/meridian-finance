package com.meridian.platform.shared.infrastructure.web;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/v1/health")
    @SecurityRequirements
    public Map<String, String> health() {
        return Map.of(
                "status", "UP",
                "app", "Meridian Platform"
        );
    }
}
