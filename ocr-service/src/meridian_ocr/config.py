from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Settings:
    database_url: str
    document_storage_root: Path
    encryption_key: str
    worker_id: str
    lease_seconds: int
    max_attempts: int
    initial_backoff_seconds: int
    polling_interval_seconds: float
    confidence_threshold: float
    google_project: str
    google_location: str
    google_processor_id: str

    @classmethod
    def from_environment(cls) -> "Settings":
        return cls(
            database_url=os.getenv(
                "OCR_DATABASE_URL",
                "postgresql://meridian_user@localhost:5432/meridian_db",
            ),
            document_storage_root=Path(
                os.getenv("MERIDIAN_DOCUMENT_STORAGE_ROOT", "/var/lib/meridian/documents")
            ),
            encryption_key=os.getenv("MERIDIAN_OCR_RESULT_ENCRYPTION_KEY", ""),
            worker_id=os.getenv("MERIDIAN_OCR_WORKER_ID", "ocr-worker"),
            lease_seconds=_positive_int("MERIDIAN_OCR_LEASE_SECONDS", 120),
            max_attempts=_positive_int("MERIDIAN_OCR_MAX_ATTEMPTS", 3),
            initial_backoff_seconds=_positive_int("MERIDIAN_OCR_INITIAL_BACKOFF_SECONDS", 15),
            polling_interval_seconds=_positive_float("MERIDIAN_OCR_POLLING_INTERVAL_SECONDS", 2.0),
            confidence_threshold=_confidence("MERIDIAN_OCR_CONFIDENCE_THRESHOLD", 0.85),
            google_project=os.getenv("GOOGLE_CLOUD_PROJECT", ""),
            google_location=os.getenv("GOOGLE_DOCUMENT_AI_LOCATION", ""),
            google_processor_id=os.getenv("GOOGLE_DOCUMENT_AI_PROCESSOR_ID", ""),
        )


def _positive_int(name: str, default: int) -> int:
    value = int(os.getenv(name, str(default)))
    if value <= 0:
        raise ValueError(f"{name} must be positive")
    return value


def _positive_float(name: str, default: float) -> float:
    value = float(os.getenv(name, str(default)))
    if value <= 0:
        raise ValueError(f"{name} must be positive")
    return value


def _confidence(name: str, default: float) -> float:
    value = float(os.getenv(name, str(default)))
    if value < 0 or value > 1:
        raise ValueError(f"{name} must be between zero and one")
    return value
