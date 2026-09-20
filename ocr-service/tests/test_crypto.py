import base64
import os

import pytest

from meridian_ocr.crypto import OcrResultCipher


def test_aes_gcm_round_trip_uses_versioned_envelope() -> None:
    cipher = OcrResultCipher(base64.b64encode(os.urandom(32)).decode("ascii"))

    envelope = cipher.encrypt("sensitive OCR value")

    assert envelope.startswith("v1:gcm:")
    assert "sensitive OCR value" not in envelope
    assert cipher.decrypt(envelope) == "sensitive OCR value"


def test_key_must_be_base64_encoded_aes_256_material() -> None:
    with pytest.raises(ValueError):
        OcrResultCipher(base64.b64encode(b"too-short").decode("ascii"))
