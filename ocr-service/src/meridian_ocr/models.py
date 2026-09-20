from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum
from typing import Any
from uuid import UUID


class FailureCategory(StrEnum):
    SOURCE_NOT_FOUND = "SOURCE_NOT_FOUND"
    SOURCE_INTEGRITY_MISMATCH = "SOURCE_INTEGRITY_MISMATCH"
    UNSUPPORTED_SOURCE = "UNSUPPORTED_SOURCE"
    PROVIDER_UNAVAILABLE = "PROVIDER_UNAVAILABLE"
    PROVIDER_REJECTED = "PROVIDER_REJECTED"
    PROVIDER_ERROR = "PROVIDER_ERROR"
    CONFIGURATION_ERROR = "CONFIGURATION_ERROR"


@dataclass(frozen=True)
class ClaimedJob:
    id: UUID
    intake_document_version_id: UUID
    evidence_type: str
    source_storage_key: str
    source_mime_type: str
    source_sha256_hex: str
    attempt_count: int
    trace_id: UUID
    lease_owner: str


@dataclass(frozen=True)
class OcrProviderResult:
    extracted_text: str
    normalized_layout: dict[str, Any]
    confidence: float | None
    provider: str
    processor_name: str
    processor_version: str | None
    processing_duration_ms: int


class OcrProcessingError(RuntimeError):
    def __init__(self, category: FailureCategory, retryable: bool) -> None:
        super().__init__(category.value)
        self.category = category
        self.retryable = retryable
