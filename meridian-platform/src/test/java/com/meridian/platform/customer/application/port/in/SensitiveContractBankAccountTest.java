package com.meridian.platform.customer.application.port.in;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SensitiveContractBankAccountTest {
    @Test void boundaryObjectRedactsAndClearsMutableValue() {
        byte[] source = "1234567890".getBytes(StandardCharsets.US_ASCII);
        SensitiveContractBankAccount account = new SensitiveContractBankAccount(UUID.randomUUID(), UUID.randomUUID(),
                "VCB", "Vietcombank", "MERIDIAN CUSTOMER", "7890", source);
        Arrays.fill(source, (byte) 0);
        assertArrayEquals("1234567890".getBytes(StandardCharsets.US_ASCII), account.copyAccountNumber());
        assertTrue(account.toString().contains("redacted"));
        assertFalse(account.toString().contains("1234567890"));
        account.close();
        assertTrue(account.cleared());
        assertThrows(IllegalStateException.class, account::copyAccountNumber);
    }

    @Test void boundaryDoesNotCarryCustomerCiphertextOrFingerprintAndUsesByteReveal() throws Exception {
        assertFalse(Arrays.stream(SensitiveContractBankAccount.class.getDeclaredFields())
                .map(Field::getName).anyMatch(name -> name.contains("ciphertext") || name.contains("fingerprint")));
        String service = Files.readString(Path.of(
                "src/main/java/com/meridian/platform/customer/application/service/ContractBankAccountService.java"));
        assertTrue(service.contains("revealToBytes"));
        assertFalse(service.contains(".reveal("));
        assertFalse(service.contains("new String"));
    }
}
