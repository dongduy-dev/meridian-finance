from __future__ import annotations

import hashlib
import json

from .crypto import OcrResultCipher
from .intake_extractor import IntakeFieldExtractor
from .models import FailureCategory, OcrProcessingError
from .provider import OcrProvider
from .repository import OcrJobRepository
from .storage import DocumentObjectStore


class OcrWorker:
    def __init__(
        self,
        repository: OcrJobRepository,
        storage: DocumentObjectStore,
        provider: OcrProvider,
        extractor: IntakeFieldExtractor,
        cipher: OcrResultCipher,
        worker_id: str,
        lease_seconds: int,
        max_attempts: int,
        initial_backoff_seconds: int,
        confidence_threshold: float,
    ) -> None:
        self._repository = repository
        self._storage = storage
        self._provider = provider
        self._extractor = extractor
        self._cipher = cipher
        self._worker_id = worker_id
        self._lease_seconds = lease_seconds
        self._max_attempts = max_attempts
        self._initial_backoff_seconds = initial_backoff_seconds
        self._confidence_threshold = confidence_threshold

    def is_ready(self) -> bool:
        return self._repository.ping() and self._provider.is_ready()

    def run_once(self) -> bool:
        if not self._provider.is_ready():
            return False
        job = self._repository.claim(self._worker_id, self._lease_seconds)
        if job is None:
            return False
        try:
            document_bytes = self._storage.read_assigned(job.source_storage_key)
            if hashlib.sha256(document_bytes).hexdigest() != job.source_sha256_hex:
                raise OcrProcessingError(
                    FailureCategory.SOURCE_INTEGRITY_MISMATCH, retryable=False
                )
            result = self._provider.process(
                document_bytes,
                job.source_mime_type,
                str(job.trace_id),
            )
            structured_suggestions = self._extractor.extract(
                job.evidence_type, result.normalized_layout
            )
            confidence = result.confidence
            disposition = (
                "HIGH_CONFIDENCE"
                if confidence is not None and confidence >= self._confidence_threshold
                else "PENDING_REVIEW"
            )
            self._repository.complete(
                job,
                result,
                self._cipher.encrypt(result.extracted_text),
                self._cipher.encrypt(_json(result.normalized_layout)),
                self._cipher.encrypt(_json(structured_suggestions)),
                disposition,
            )
        except OcrProcessingError as exc:
            self._repository.record_failure(
                job,
                exc.category,
                exc.retryable,
                self._max_attempts,
                self._initial_backoff_seconds,
            )
        except Exception:
            self._repository.record_failure(
                job,
                FailureCategory.PROVIDER_ERROR,
                True,
                self._max_attempts,
                self._initial_backoff_seconds,
            )
        return True


def _json(value: object) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
