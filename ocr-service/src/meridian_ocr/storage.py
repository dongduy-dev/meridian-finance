from __future__ import annotations

from pathlib import Path

from .models import FailureCategory, OcrProcessingError


class DocumentObjectStore:
    def __init__(self, storage_root: Path) -> None:
        self._objects_root = (storage_root / "objects").resolve()

    def read_assigned(self, storage_key: str) -> bytes:
        if (
            not storage_key
            or "\\" in storage_key
            or storage_key.startswith("/")
            or ".." in storage_key
        ):
            raise OcrProcessingError(FailureCategory.SOURCE_NOT_FOUND, retryable=False)
        candidate = (self._objects_root / storage_key).resolve()
        if not candidate.is_relative_to(self._objects_root):
            raise OcrProcessingError(FailureCategory.SOURCE_NOT_FOUND, retryable=False)
        try:
            if candidate.is_symlink() or not candidate.is_file():
                raise OcrProcessingError(FailureCategory.SOURCE_NOT_FOUND, retryable=False)
            return candidate.read_bytes()
        except OcrProcessingError:
            raise
        except OSError as exc:
            raise OcrProcessingError(FailureCategory.SOURCE_NOT_FOUND, retryable=True) from exc
