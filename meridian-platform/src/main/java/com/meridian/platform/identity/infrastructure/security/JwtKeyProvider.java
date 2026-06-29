package com.meridian.platform.identity.infrastructure.security;

import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;

@Component
public class JwtKeyProvider {

    private final KeyPair keyPair;

    public JwtKeyProvider() {
        this(generateKeyPair());
    }

    JwtKeyProvider(KeyPair keyPair) {
        this.keyPair = keyPair;
    }

    public PrivateKey privateKey() {
        return keyPair.getPrivate();
    }

    public PublicKey publicKey() {
        return keyPair.getPublic();
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("RSA key generation is not available.", exception);
        }
    }
}
