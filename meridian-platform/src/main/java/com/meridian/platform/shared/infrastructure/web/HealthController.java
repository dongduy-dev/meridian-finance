package com.meridian.platform.shared.infrastructure.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping({"/api/health", "/api/v1/health"})
    public Map<String, String> health() {
        return Map.of(
                "status", "UP",
                "app", "Meridian Platform"
        );
    }
}
