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


def test_java_python_aes_gcm_compatibility_vector() -> None:
    cipher = OcrResultCipher(
        "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
    )

    assert cipher.decrypt(
        "v1:gcm:AAECAwQFBgcICQoL:"
        "HHn0fayArn_DIPruk9NaC_a663qRFjpeFEWV93IZb8FkdPidw7R3uk6GMYr9_k1Wzg8B43qXzfgTtUl2doWcipVSpR_x6xZPJWzXM73cyovyMiddLI6C0s8R_UM"
    ) == (
        '[{"fieldName":"fullName","proposedValue":"Nguyen Van An","confidence":0.98}]'
    )
