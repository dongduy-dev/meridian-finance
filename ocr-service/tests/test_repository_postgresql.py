from __future__ import annotations

import os
from datetime import UTC, datetime, timedelta

import psycopg

from meridian_ocr.models import FailureCategory, OcrProviderResult

from conftest import insert_job


def test_claims_pending_jobs_and_skips_a_row_locked_by_another_worker(database_repository) -> None:
    repository, schema = database_repository
    database_url = os.environ["OCR_TEST_DATABASE_URL"]
    first = insert_job(database_url, schema, source_storage_key="aa/first")
    second = insert_job(database_url, schema, source_storage_key="bb/second")
    with psycopg.connect(database_url) as locking:
        locking.execute(f'SET search_path TO "{schema}"')
        locking.execute("SELECT id FROM ocr_jobs WHERE id = %s FOR UPDATE", (first["id"],))

        claimed = repository.claim("worker-b", 60)

        assert claimed is not None
        assert claimed.id == second["id"]
        assert claimed.attempt_count == 1


def test_expired_processing_lease_is_reclaimed(database_repository) -> None:
    repository, schema = database_repository
    values = insert_job(
        os.environ["OCR_TEST_DATABASE_URL"],
        schema,
        state="PROCESSING",
        lease_owner="crashed-worker",
        lease_expires_at=datetime.now(UTC).replace(tzinfo=None) - timedelta(seconds=1),
        attempt_count=1,
    )

    claimed = repository.claim("recovery-worker", 60)

    assert claimed is not None
    assert claimed.id == values["id"]
    assert claimed.lease_owner == "recovery-worker"
    assert claimed.attempt_count == 2


def test_retryable_failure_returns_to_pending_until_max_attempts(database_repository) -> None:
    repository, schema = database_repository
    insert_job(os.environ["OCR_TEST_DATABASE_URL"], schema)
    claimed = repository.claim("worker", 60)
    assert claimed is not None

    assert repository.record_failure(
        claimed, FailureCategory.PROVIDER_UNAVAILABLE, True, 3, 1
    )
    state = _row(schema, claimed.id)
    assert state["state"] == "PENDING"
    assert state["failure_category"] == "PROVIDER_UNAVAILABLE"
    assert state["lease_owner"] is None


def test_max_attempts_and_non_retryable_failures_end_in_failed(database_repository) -> None:
    repository, schema = database_repository
    insert_job(os.environ["OCR_TEST_DATABASE_URL"], schema, attempt_count=2)
    exhausted = repository.claim("worker", 60)
    assert exhausted is not None
    repository.record_failure(exhausted, FailureCategory.PROVIDER_ERROR, True, 3, 1)
    assert _row(schema, exhausted.id)["state"] == "FAILED"

    insert_job(os.environ["OCR_TEST_DATABASE_URL"], schema)
    rejected = repository.claim("worker", 60)
    assert rejected is not None
    repository.record_failure(
        rejected, FailureCategory.SOURCE_INTEGRITY_MISMATCH, False, 3, 1
    )
    row = _row(schema, rejected.id)
    assert row["state"] == "FAILED"
    assert row["failure_category"] == "SOURCE_INTEGRITY_MISMATCH"


def test_result_insert_and_completion_are_atomic_and_exactly_once(database_repository) -> None:
    repository, schema = database_repository
    insert_job(os.environ["OCR_TEST_DATABASE_URL"], schema)
    claimed = repository.claim("worker", 60)
    assert claimed is not None
    result = OcrProviderResult(
        extracted_text="not persisted directly",
        normalized_layout={"pages": []},
        confidence=0.91,
        provider="TEST",
        processor_name="synthetic",
        processor_version=None,
        processing_duration_ms=12,
    )

    assert repository.complete(
        claimed,
        result,
        "v1:gcm:nonce:ciphertext",
        "v1:gcm:nonce:ciphertext",
        "v1:gcm:nonce:ciphertext",
        "HIGH_CONFIDENCE",
    )
    assert repository.complete(
        claimed,
        result,
        "v1:gcm:nonce:ciphertext",
        "v1:gcm:nonce:ciphertext",
        "v1:gcm:nonce:ciphertext",
        "HIGH_CONFIDENCE",
    ) is False
    row = _row(schema, claimed.id)
    assert row["state"] == "COMPLETED"
    assert _result_count(schema, claimed.id) == 1


def test_expired_worker_cannot_complete_or_record_failure(database_repository) -> None:
    repository, schema = database_repository
    insert_job(os.environ["OCR_TEST_DATABASE_URL"], schema)
    claimed = repository.claim("expired-worker", 60)
    assert claimed is not None
    repository.extend_lease_for_test(
        claimed.id, datetime.now(UTC) - timedelta(seconds=1)
    )
    result = OcrProviderResult(
        extracted_text="not persisted directly",
        normalized_layout={"pages": []},
        confidence=0.91,
        provider="TEST",
        processor_name="synthetic",
        processor_version=None,
        processing_duration_ms=12,
    )

    assert repository.complete(
        claimed,
        result,
        "v1:gcm:nonce:ciphertext",
        "v1:gcm:nonce:ciphertext",
        "v1:gcm:nonce:ciphertext",
        "HIGH_CONFIDENCE",
    ) is False
    assert repository.record_failure(
        claimed, FailureCategory.PROVIDER_ERROR, True, 3, 1
    ) is False
    assert _row(schema, claimed.id)["state"] == "PROCESSING"
    assert _result_count(schema, claimed.id) == 0


def _row(schema: str, job_id):
    with psycopg.connect(os.environ["OCR_TEST_DATABASE_URL"], row_factory=psycopg.rows.dict_row) as connection:
        connection.execute(f'SET search_path TO "{schema}"')
        return connection.execute("SELECT * FROM ocr_jobs WHERE id = %s", (job_id,)).fetchone()


def _result_count(schema: str, job_id) -> int:
    with psycopg.connect(os.environ["OCR_TEST_DATABASE_URL"]) as connection:
        connection.execute(f'SET search_path TO "{schema}"')
        return connection.execute(
            "SELECT count(*) FROM ocr_results WHERE ocr_job_id = %s", (job_id,)
        ).fetchone()[0]
