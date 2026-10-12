package com.meridian.platform.identity.infrastructure.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationClientAddressResolverTest {

    private final AuthenticationClientAddressResolver cloudflare = new AuthenticationClientAddressResolver(
            AuthenticationClientAddressResolver.Mode.CLOUDFLARE);

    @Test
    void defaultModeIgnoresAllProxyHeaders() {
        var request = request("not-an-address");
        request.setRemoteAddr("192.0.2.9");
        request.addHeader("X-Forwarded-For", "198.51.100.1");
        request.addHeader("Forwarded", "for=198.51.100.2");
        var resolver = new AuthenticationClientAddressResolver(AuthenticationClientAddressResolver.Mode.REMOTE_ADDR);
        assertThat(resolver.resolve(request)).contains("192.0.2.9");
    }

    @ParameterizedTest
    @CsvSource({
            "198.51.100.1,198.51.100.1",
            "2001:db8::a,2001:db8:0:0:0:0:0:a",
            "2001:0DB8:0:0:0:0:0:000A,2001:db8:0:0:0:0:0:a",
            "::ffff:198.51.100.1,198.51.100.1"
    })
    void canonicalizesLiteralAddressesWithoutDns(String header, String expected) {
        assertThat(cloudflare.resolve(request(header))).contains(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"example.com", "localhost", "198.51.100.1, 203.0.113.1",
            "198.51.100.1:443", "[2001:db8::1]", "fe80::1%eth0", "fe80::1%1", "127.1",
            "2130706433", "0x7f000001", "0198.51.100.1", "256.1.1.1", "2001:::1",
            " 198.51.100.1", "198.51.100.1 ", "unknown", "for=198.51.100.1",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void rejectsMissingMalformedAmbiguousOrNonLiteralMetadata(String header) {
        var request = request(header);
        request.addHeader("X-Forwarded-For", "198.51.100.1");
        assertThat(cloudflare.resolve(request)).isEmpty();
    }

    @Test
    void rejectsMultipleHeaderOccurrencesEvenWhenIdentical() {
        var request = request("198.51.100.1");
        request.addHeader("CF-Connecting-IP", "198.51.100.1");
        assertThat(cloudflare.resolve(request)).isEmpty();
    }

    private static MockHttpServletRequest request(String header) {
        var request = new MockHttpServletRequest();
        if (header != null) request.addHeader("CF-Connecting-IP", header);
        return request;
    }
}
