from __future__ import annotations

import base64
import hashlib
import os
from pathlib import Path
from unittest.mock import Mock
from uuid import uuid4

from meridian_ocr.crypto import OcrResultCipher
from meridian_ocr.models import ClaimedJob, FailureCategory, OcrProviderResult
from meridian_ocr.storage import DocumentObjectStore
from meridian_ocr.worker import OcrWorker


def test_provider_not_ready_leaves_queue_unclaimed(tmp_path: Path) -> None:
    repository = Mock()
    provider = Mock()
    provider.is_ready.return_value = False
    worker = _worker(repository, provider, tmp_path)

    assert worker.run_once() is False
    repository.claim.assert_not_called()


def test_sha256_mismatch_fails_without_provider_call(tmp_path: Path) -> None:
    repository = Mock()
    provider = Mock()
    provider.is_ready.return_value = True
    job = _stored_job(tmp_path, b"actual bytes", "0" * 64)
    repository.claim.return_value = job
    worker = _worker(repository, provider, tmp_path)

    assert worker.run_once()

    provider.process.assert_not_called()
    args = repository.record_failure.call_args.args
    assert args[1] == FailureCategory.SOURCE_INTEGRITY_MISMATCH
    assert args[2] is False


def test_success_encrypts_all_sensitive_payloads_before_persistence(tmp_path: Path) -> None:
    repository = Mock()
    provider = Mock()
    provider.is_ready.return_value = True
    content = b"%PDF-controlled-test"
    repository.claim.return_value = _stored_job(
        tmp_path, content, hashlib.sha256(content).hexdigest()
    )
    provider.process.return_value = OcrProviderResult(
        extracted_text="identity 123456789",
        normalized_layout={"pages": [{"text": "identity 123456789"}]},
        structured_suggestions=[],
        confidence=0.9,
        provider="TEST",
        processor_name="test-provider",
        processor_version="1",
        processing_duration_ms=5,
    )
    worker = _worker(repository, provider, tmp_path)

    assert worker.run_once()

    complete = repository.complete.call_args.args
    assert complete[2].startswith("v1:gcm:")
    assert complete[3].startswith("v1:gcm:")
    assert complete[4].startswith("v1:gcm:")
    assert "identity 123456789" not in "".join(complete[2:5])
    assert complete[5] == "HIGH_CONFIDENCE"


def _worker(repository: Mock, provider: Mock, storage_root: Path) -> OcrWorker:
    cipher = OcrResultCipher(base64.b64encode(os.urandom(32)).decode("ascii"))
    return OcrWorker(
        repository,
        DocumentObjectStore(storage_root),
        provider,
        cipher,
        "worker",
        60,
        3,
        1,
        0.85,
    )


def _stored_job(storage_root: Path, content: bytes, expected_hash: str) -> ClaimedJob:
    path = storage_root / "objects" / "ab" / "object"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(content)
    return ClaimedJob(
        id=uuid4(),
        intake_document_version_id=uuid4(),
        evidence_type="CUSTOMER_IDENTITY",
        source_storage_key="ab/object",
        source_mime_type="application/pdf",
        source_sha256_hex=expected_hash,
        attempt_count=1,
        trace_id=uuid4(),
        lease_owner="worker",
    )
