package com.meridian.platform.loan.infrastructure.adapter.out.crypto;

import com.meridian.platform.loan.application.port.out.DisbursementBankAccountProtectionContext;
import com.meridian.platform.loan.application.port.out.DisbursementBankAccountProtector;
import com.meridian.platform.loan.application.port.out.ProtectedBankAccountEnvelope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

@Component
public class AesGcmDisbursementBankAccountProtector implements DisbursementBankAccountProtector {
    static final String SCHEME = "AES-256-GCM";
    static final String AAD_VERSION = "DISBURSEMENT_ACCOUNT_V1";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final DisbursementSnapshotKeyProperties properties;
    private final SecureRandom random;

    @Autowired
    public AesGcmDisbursementBankAccountProtector(DisbursementSnapshotKeyProperties properties) {
        this(properties, new SecureRandom());
    }

    AesGcmDisbursementBankAccountProtector(DisbursementSnapshotKeyProperties properties, SecureRandom random) {
        this.properties = properties;
        this.random = random;
    }

    @Override
    public ProtectedBankAccountEnvelope protect(byte[] accountNumber, DisbursementBankAccountProtectionContext context) {
        if (accountNumber == null || accountNumber.length == 0) {
            throw new IllegalArgumentException("Account number is required for protection.");
        }
        String keyId = properties.getActiveKeyId();
        byte[] key = properties.resolve(keyId);
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad(context));
            return new ProtectedBankAccountEnvelope(SCHEME, keyId, nonce, cipher.doFinal(accountNumber), AAD_VERSION);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Disbursement bank account could not be protected.");
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    @Override
    public byte[] revealToBytes(ProtectedBankAccountEnvelope envelope, DisbursementBankAccountProtectionContext context) {
        if (!SCHEME.equals(envelope.protectionScheme()) || !AAD_VERSION.equals(envelope.aadVersion())) {
            throw new IllegalStateException("Disbursement bank account envelope is unsupported.");
        }
        byte[] key = properties.resolve(envelope.keyId());
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, envelope.nonce()));
            cipher.updateAAD(aad(context));
            return cipher.doFinal(envelope.ciphertext());
        } catch (AEADBadTagException exception) {
            throw new IllegalStateException("Disbursement bank account envelope failed authentication.");
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Disbursement bank account envelope could not be decrypted.");
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    private static byte[] aad(DisbursementBankAccountProtectionContext context) {
        byte[] purpose = AAD_VERSION.getBytes(StandardCharsets.US_ASCII);
        ByteBuffer buffer = ByteBuffer.allocate(purpose.length + 1 + 64);
        buffer.put(purpose).put((byte) 0);
        putUuid(buffer, context.contractId());
        putUuid(buffer, context.loanApplicationId());
        putUuid(buffer, context.customerId());
        putUuid(buffer, context.sourceBankAccountId());
        return buffer.array();
    }

    private static void putUuid(ByteBuffer buffer, java.util.UUID value) {
        buffer.putLong(value.getMostSignificantBits()).putLong(value.getLeastSignificantBits());
    }
}
