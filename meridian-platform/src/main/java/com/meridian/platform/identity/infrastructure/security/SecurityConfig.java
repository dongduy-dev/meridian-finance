package com.meridian.platform.identity.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/health",
                                "/api/v1/health",
                                "/api/v1/loan-products",
                                "/api/v1/loan-products/**",
                                "/api/v1/partner-companies",
                                "/api/v1/partner-companies/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .build();
    }
}
