from __future__ import annotations

import base64
import os

from cryptography.hazmat.primitives.ciphers.aead import AESGCM


class OcrResultCipher:
    PREFIX = "v1:gcm"

    def __init__(self, base64_key: str) -> None:
        try:
            key = base64.b64decode(base64_key, validate=True)
        except (ValueError, TypeError) as exc:
            raise ValueError("OCR result encryption key must be valid Base64") from exc
        if len(key) != 32:
            raise ValueError("OCR result encryption key must decode to 32 bytes")
        self._cipher = AESGCM(key)

    def encrypt(self, value: str) -> str:
        nonce = os.urandom(12)
        ciphertext = self._cipher.encrypt(nonce, value.encode("utf-8"), None)
        return f"{self.PREFIX}:{_encode(nonce)}:{_encode(ciphertext)}"

    def decrypt(self, envelope: str) -> str:
        parts = envelope.split(":")
        if len(parts) != 4 or parts[0:2] != ["v1", "gcm"]:
            raise ValueError("Unsupported OCR encryption envelope")
        plaintext = self._cipher.decrypt(_decode(parts[2]), _decode(parts[3]), None)
        return plaintext.decode("utf-8")


def _encode(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode("ascii")


def _decode(value: str) -> bytes:
    padding = "=" * (-len(value) % 4)
    return base64.urlsafe_b64decode(value + padding)
