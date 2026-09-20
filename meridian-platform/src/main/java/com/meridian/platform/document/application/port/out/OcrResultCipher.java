package com.meridian.platform.document.application.port.out;

public interface OcrResultCipher {

    String encrypt(String plaintext);

    String decrypt(String envelope);
}
