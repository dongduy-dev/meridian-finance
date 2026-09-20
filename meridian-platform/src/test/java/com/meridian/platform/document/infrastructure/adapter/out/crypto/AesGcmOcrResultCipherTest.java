package com.meridian.platform.document.infrastructure.adapter.out.crypto;

import com.meridian.platform.shared.domain.exception.ServiceUnavailableException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AesGcmOcrResultCipherTest {

    @Test
    void decryptsThePythonAesGcmCompatibilityVector() {
        var cipher = new AesGcmOcrResultCipher(
                "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
        );

        assertEquals(
                "[{\"fieldName\":\"fullName\",\"proposedValue\":\"Nguyen Van An\",\"confidence\":0.98}]",
                cipher.decrypt("v1:gcm:AAECAwQFBgcICQoL:HHn0fayArn_DIPruk9NaC_a663qRFjpeFEWV93IZb8FkdPidw7R3uk6GMYr9_k1Wzg8B43qXzfgTtUl2doWcipVSpR_x6xZPJWzXM73cyovyMiddLI6C0s8R_UM")
        );
    }

    @Test
    void missingOrInvalidKeyDoesNotPreventConstructionButFailsTheCapabilitySafely() {
        var missing = new AesGcmOcrResultCipher("");
        var invalid = new AesGcmOcrResultCipher("not-base64");

        assertThrows(ServiceUnavailableException.class, () -> missing.decrypt("v1:gcm:a:b"));
        assertThrows(ServiceUnavailableException.class, () -> invalid.encrypt("sensitive"));
    }
}
