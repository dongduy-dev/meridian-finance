package com.meridian.platform.identity.infrastructure.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.util.Optional;
import java.util.regex.Pattern;

/** Resolves only authentication throttling identity; never rewrites servlet request metadata. */
final class AuthenticationClientAddressResolver {

    enum Mode { REMOTE_ADDR, CLOUDFLARE }

    private static final String HEADER = "CF-Connecting-IP";
    private static final Pattern IPV4 = Pattern.compile("(?:0|[1-9][0-9]{0,2})(?:\\.(?:0|[1-9][0-9]{0,2})){3}");
    private static final Pattern IPV6_CHARACTERS = Pattern.compile("[0-9a-fA-F:.]+");

    private final Mode mode;

    AuthenticationClientAddressResolver(Mode mode) {
        this.mode = java.util.Objects.requireNonNull(mode);
    }

    Optional<String> resolve(HttpServletRequest request) {
        if (mode == Mode.REMOTE_ADDR) {
            return Optional.ofNullable(request.getRemoteAddr());
        }

        // This mode requires the isolated Tunnel-only origin. The header is not authentication.
        var values = request.getHeaders(HEADER);
        if (values == null || !values.hasMoreElements()) {
            return Optional.empty();
        }
        String value = values.nextElement();
        if (values.hasMoreElements() || value == null || value.length() > 45) {
            return Optional.empty();
        }
        boolean ipv6 = value.indexOf(':') >= 0;
        if (!(ipv6 ? IPV6_CHARACTERS.matcher(value).matches() : IPV4.matcher(value).matches())) {
            return Optional.empty();
        }
        try {
            // Java's literal-only parser does no DNS lookup. Canonicalization prevents IPv6 aliases
            // (including mapped IPv4) from creating separate rate-limit buckets.
            return Optional.of(InetAddress.ofLiteral(value).getHostAddress());
        } catch (IllegalArgumentException invalidLiteral) {
            return Optional.empty();
        }
    }
}
