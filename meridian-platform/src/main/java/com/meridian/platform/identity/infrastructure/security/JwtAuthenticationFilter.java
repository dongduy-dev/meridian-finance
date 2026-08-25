package com.meridian.platform.identity.infrastructure.security;

import com.meridian.platform.identity.application.port.out.AccessTokenRevocationRepository;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Set<String> UNFILTERED_PATHS = Set.of(
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout"
    );

    private final JwtTokenService jwtTokenService;
    private final AccessTokenRevocationRepository accessTokenRevocationRepository;
    private final SecurityErrorResponseWriter errorResponseWriter;

    public JwtAuthenticationFilter(
            JwtTokenService jwtTokenService,
            AccessTokenRevocationRepository accessTokenRevocationRepository,
            SecurityErrorResponseWriter errorResponseWriter
    ) {
        this.jwtTokenService = jwtTokenService;
        this.accessTokenRevocationRepository = accessTokenRevocationRepository;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return UNFILTERED_PATHS.contains(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            ParsedAccessToken parsedAccessToken = jwtTokenService.parseAccessTokenDetails(authorizationHeader.substring(7));
            if (accessTokenRevocationRepository.isRevoked(parsedAccessToken.tokenId())) {
                throw new JwtAuthenticationException("INVALID_TOKEN", "Invalid token.");
            }
            AuthenticatedUser authenticatedUser = parsedAccessToken.authenticatedUser();
            MeridianPrincipal principal = new MeridianPrincipal(authenticatedUser);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    grantedAuthorities(authenticatedUser)
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authentication);
            SecurityContextHolder.setContext(securityContext);
            filterChain.doFilter(request, response);
        } catch (JwtAuthenticationException exception) {
            SecurityContextHolder.clearContext();
            errorResponseWriter.write(
                    request,
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    exception.getErrorCode(),
                    exception.getMessage()
            );
        }
    }

    private Set<SimpleGrantedAuthority> grantedAuthorities(AuthenticatedUser authenticatedUser) {
        Set<SimpleGrantedAuthority> authorities = new LinkedHashSet<>();
        authenticatedUser.permissions().stream()
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);
        authenticatedUser.roles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .forEach(authorities::add);
        return authorities;
    }
}
