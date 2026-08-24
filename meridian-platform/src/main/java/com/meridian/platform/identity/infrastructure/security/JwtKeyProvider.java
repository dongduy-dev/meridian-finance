package com.meridian.platform.identity.infrastructure.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Component
public class JwtKeyProvider {

    private static final int MINIMUM_RSA_BITS = 2048;

    private final KeyPair keyPair;

    @Autowired
    public JwtKeyProvider(
            @Value("${meridian.identity.jwt.private-key:}") String encodedPrivateKey,
            @Value("${meridian.identity.jwt.public-key:}") String encodedPublicKey
    ) {
        this(loadKeyPair(encodedPrivateKey, encodedPublicKey));
    }

    JwtKeyProvider(KeyPair keyPair) {
        this.keyPair = validateKeyPair(keyPair);
    }

    public PrivateKey privateKey() {
        return keyPair.getPrivate();
    }

    public PublicKey publicKey() {
        return keyPair.getPublic();
    }

    private static KeyPair loadKeyPair(String encodedPrivateKey, String encodedPublicKey) {
        if (encodedPrivateKey == null || encodedPrivateKey.isBlank()
                || encodedPublicKey == null || encodedPublicKey.isBlank()) {
            throw new IllegalStateException(
                    "JWT signing-key configuration is incomplete: both PKCS#8 private and X.509 public keys are required."
            );
        }

        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(
                    Base64.getDecoder().decode(encodedPrivateKey.trim())
            ));
            PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(
                    Base64.getDecoder().decode(encodedPublicKey.trim())
            ));
            return validateKeyPair(new KeyPair(publicKey, privateKey));
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "JWT signing-key configuration is malformed or does not contain matching RSA keys.",
                    exception
            );
        }
    }

    private static KeyPair validateKeyPair(KeyPair keyPair) {
        if (!(keyPair.getPrivate() instanceof RSAPrivateKey privateKey)
                || !(keyPair.getPublic() instanceof RSAPublicKey publicKey)) {
            throw new IllegalStateException("JWT signing keys must be RSA keys.");
        }
        if (privateKey.getModulus().bitLength() < MINIMUM_RSA_BITS
                || publicKey.getModulus().bitLength() < MINIMUM_RSA_BITS) {
            throw new IllegalStateException("JWT signing keys must use RSA-2048 or stronger material.");
        }
        if (!privateKey.getModulus().equals(publicKey.getModulus())) {
            throw new IllegalStateException("JWT private and public keys do not match.");
        }

        try {
            byte[] proof = "meridian-jwt-key-validation".getBytes(StandardCharsets.UTF_8);
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(privateKey);
            signer.update(proof);
            byte[] signature = signer.sign();

            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(proof);
            if (!verifier.verify(signature)) {
                throw new IllegalStateException("JWT private and public keys do not match.");
            }
            return keyPair;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("JWT signing keys could not be validated.", exception);
        }
    }
}
