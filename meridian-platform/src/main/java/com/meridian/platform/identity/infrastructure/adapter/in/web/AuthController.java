package com.meridian.platform.identity.infrastructure.adapter.in.web;

import com.meridian.platform.identity.application.dto.AuthResponse;
import com.meridian.platform.identity.application.dto.AuthenticationResult;
import com.meridian.platform.identity.application.dto.LoginRequest;
import com.meridian.platform.identity.application.port.in.AuthenticationUseCase;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationUseCase authenticationUseCase;
    private final RefreshTokenCookieService refreshTokenCookieService;

    public AuthController(
            AuthenticationUseCase authenticationUseCase,
            RefreshTokenCookieService refreshTokenCookieService
    ) {
        this.authenticationUseCase = authenticationUseCase;
        this.refreshTokenCookieService = refreshTokenCookieService;
    }

    @PostMapping("/login")
    @SecurityRequirements
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return response(authenticationUseCase.login(request));
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
        String refreshToken = refreshTokenCookieService.read(request).orElse(null);
        return response(authenticationUseCase.refresh(refreshToken));
    }

    private ResponseEntity<AuthResponse> response(AuthenticationResult result) {
        String refreshCookie = refreshTokenCookieService.issue(
                result.refreshToken(),
                result.refreshTokenIssuedAt(),
                result.refreshTokenExpiresAt()
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie)
                .body(result.response());
    }
}
