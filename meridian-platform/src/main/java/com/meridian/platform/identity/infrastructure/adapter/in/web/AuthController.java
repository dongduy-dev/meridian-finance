package com.meridian.platform.identity.infrastructure.adapter.in.web;

import com.meridian.platform.identity.application.dto.AuthResponse;
import com.meridian.platform.identity.application.dto.AccessTokenReference;
import com.meridian.platform.identity.application.dto.AuthenticationResult;
import com.meridian.platform.identity.application.dto.CurrentSessionLogoutCommand;
import com.meridian.platform.identity.application.dto.CustomerRegistrationRequest;
import com.meridian.platform.identity.application.dto.CustomerRegistrationResponse;
import com.meridian.platform.identity.application.dto.EmailVerificationConfirmationRequest;
import com.meridian.platform.identity.application.dto.EmailVerificationRequest;
import com.meridian.platform.identity.application.dto.LoginRequest;
import com.meridian.platform.identity.application.port.in.AuthenticationUseCase;
import com.meridian.platform.identity.application.port.in.ConfirmEmailVerificationUseCase;
import com.meridian.platform.identity.application.port.in.LogoutUseCase;
import com.meridian.platform.identity.application.port.in.RegisterCustomerUseCase;
import com.meridian.platform.identity.application.port.in.RequestEmailVerificationUseCase;
import com.meridian.platform.identity.infrastructure.security.JwtAuthenticationException;
import com.meridian.platform.identity.infrastructure.security.JwtTokenService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationUseCase authenticationUseCase;
    private final LogoutUseCase logoutUseCase;
    private final RegisterCustomerUseCase registerCustomerUseCase;
    private final RequestEmailVerificationUseCase requestEmailVerificationUseCase;
    private final ConfirmEmailVerificationUseCase confirmEmailVerificationUseCase;
    private final RefreshTokenCookieService refreshTokenCookieService;
    private final JwtTokenService jwtTokenService;

    public AuthController(
            AuthenticationUseCase authenticationUseCase,
            LogoutUseCase logoutUseCase,
            RegisterCustomerUseCase registerCustomerUseCase,
            RequestEmailVerificationUseCase requestEmailVerificationUseCase,
            ConfirmEmailVerificationUseCase confirmEmailVerificationUseCase,
            RefreshTokenCookieService refreshTokenCookieService,
            JwtTokenService jwtTokenService
    ) {
        this.authenticationUseCase = authenticationUseCase;
        this.logoutUseCase = logoutUseCase;
        this.registerCustomerUseCase = registerCustomerUseCase;
        this.requestEmailVerificationUseCase = requestEmailVerificationUseCase;
        this.confirmEmailVerificationUseCase = confirmEmailVerificationUseCase;
        this.refreshTokenCookieService = refreshTokenCookieService;
        this.jwtTokenService = jwtTokenService;
    }

    @PostMapping("/login")
    @SecurityRequirements
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return response(authenticationUseCase.login(request));
    }

    @PostMapping("/register")
    @SecurityRequirements
    public ResponseEntity<CustomerRegistrationResponse> register(
            @Valid @RequestBody CustomerRegistrationRequest request
    ) {
        return ResponseEntity.status(201).body(registerCustomerUseCase.register(request));
    }

    @PostMapping("/email-verification/request")
    @SecurityRequirements
    public ResponseEntity<Void> requestEmailVerification(
            @Valid @RequestBody EmailVerificationRequest request
    ) {
        requestEmailVerificationUseCase.requestVerification(request);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/email-verification/confirm")
    @SecurityRequirements
    public ResponseEntity<Void> confirmEmailVerification(
            @Valid @RequestBody EmailVerificationConfirmationRequest request
    ) {
        confirmEmailVerificationUseCase.confirmVerification(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
        String refreshToken = refreshTokenCookieService.read(request).orElse(null);
        return response(authenticationUseCase.refresh(refreshToken));
    }

    @PostMapping("/logout")
    @SecurityRequirements
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        logoutUseCase.logout(new CurrentSessionLogoutCommand(
                refreshTokenCookieService.read(request),
                readValidAccessToken(request)
        ));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieService.clear())
                .build();
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

    private Optional<AccessTokenReference> readValidAccessToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }

        try {
            var parsedToken = jwtTokenService.parseAccessTokenDetails(authorizationHeader.substring(7));
            return Optional.of(new AccessTokenReference(parsedToken.tokenId(), parsedToken.expiresAt()));
        } catch (JwtAuthenticationException exception) {
            return Optional.empty();
        }
    }
}
