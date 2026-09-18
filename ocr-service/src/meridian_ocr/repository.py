from __future__ import annotations

from contextlib import contextmanager
from datetime import UTC, datetime, timedelta
from typing import Iterator
from uuid import UUID, uuid4

import psycopg
from psycopg.rows import dict_row
from psycopg_pool import ConnectionPool

from .models import ClaimedJob, FailureCategory, OcrProviderResult


class OcrJobRepository:
    def __init__(self, database_url: str) -> None:
        self._pool = ConnectionPool(database_url, min_size=0, max_size=4, open=False)
        self._pool.open(wait=False)

    def close(self) -> None:
        self._pool.close()

    def ping(self) -> bool:
        try:
            with self._pool.connection() as connection:
                return connection.execute("SELECT 1").fetchone()[0] == 1
        except psycopg.Error:
            return False

    def claim(self, worker_id: str, lease_seconds: int) -> ClaimedJob | None:
        with self._connection() as connection:
            row = connection.execute(
                """
                WITH candidate AS (
                    SELECT id
                    FROM ocr_jobs
                    WHERE (state = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP)
                       OR (state = 'PROCESSING' AND lease_expires_at <= CURRENT_TIMESTAMP)
                    ORDER BY
                        CASE WHEN state = 'PROCESSING' THEN 0 ELSE 1 END,
                        COALESCE(lease_expires_at, next_attempt_at), created_at, id
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                UPDATE ocr_jobs job
                SET state = 'PROCESSING',
                    lease_owner = %s,
                    lease_expires_at = CURRENT_TIMESTAMP + (%s * INTERVAL '1 second'),
                    attempt_count = job.attempt_count + 1,
                    updated_at = CURRENT_TIMESTAMP
                FROM candidate
                WHERE job.id = candidate.id
                RETURNING job.id, job.intake_document_version_id, job.evidence_type,
                          job.source_storage_key, job.source_mime_type, job.source_sha256_hex,
                          job.attempt_count, job.trace_id, job.lease_owner
                """,
                (worker_id, lease_seconds),
            ).fetchone()
            if row is None:
                return None
            return ClaimedJob(**row)

    def complete(
        self,
        job: ClaimedJob,
        result: OcrProviderResult,
        encrypted_text: str,
        encrypted_layout: str,
        encrypted_suggestions: str,
        disposition: str,
    ) -> bool:
        with self._connection() as connection:
            locked = connection.execute(
                """
                SELECT id FROM ocr_jobs
                WHERE id = %s AND state = 'PROCESSING' AND lease_owner = %s
                  AND lease_expires_at > CURRENT_TIMESTAMP
                FOR UPDATE
                """,
                (job.id, job.lease_owner),
            ).fetchone()
            if locked is None:
                return False
            connection.execute(
                """
                INSERT INTO ocr_results (
                    id, ocr_job_id, provider, processor_name, processor_version,
                    encrypted_extracted_text, encrypted_normalized_payload,
                    encrypted_structured_suggestions, normalized_confidence,
                    disposition, processing_duration_ms, created_at
                ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, CURRENT_TIMESTAMP)
                """,
                (
                    uuid4(), job.id, result.provider, result.processor_name,
                    result.processor_version, encrypted_text, encrypted_layout,
                    encrypted_suggestions, result.confidence, disposition,
                    result.processing_duration_ms,
                ),
            )
            connection.execute(
                """
                UPDATE ocr_jobs
                SET state = 'COMPLETED', lease_owner = NULL, lease_expires_at = NULL,
                    failure_category = NULL, completed_at = CURRENT_TIMESTAMP,
                    failed_at = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE id = %s
                """,
                (job.id,),
            )
            return True

    def record_failure(
        self,
        job: ClaimedJob,
        category: FailureCategory,
        retryable: bool,
        max_attempts: int,
        initial_backoff_seconds: int,
    ) -> bool:
        exhausted = job.attempt_count >= max_attempts
        should_fail = not retryable or exhausted
        with self._connection() as connection:
            if should_fail:
                cursor = connection.execute(
                    """
                    UPDATE ocr_jobs
                    SET state = 'FAILED', lease_owner = NULL, lease_expires_at = NULL,
                        failure_category = %s, failed_at = CURRENT_TIMESTAMP,
                        completed_at = NULL, updated_at = CURRENT_TIMESTAMP
                    WHERE id = %s AND state = 'PROCESSING' AND lease_owner = %s
                      AND lease_expires_at > CURRENT_TIMESTAMP
                    """,
                    (category.value, job.id, job.lease_owner),
                )
            else:
                backoff = initial_backoff_seconds * (2 ** (job.attempt_count - 1))
                cursor = connection.execute(
                    """
                    UPDATE ocr_jobs
                    SET state = 'PENDING', lease_owner = NULL, lease_expires_at = NULL,
                        failure_category = %s,
                        next_attempt_at = CURRENT_TIMESTAMP + (%s * INTERVAL '1 second'),
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = %s AND state = 'PROCESSING' AND lease_owner = %s
                      AND lease_expires_at > CURRENT_TIMESTAMP
                    """,
                    (category.value, backoff, job.id, job.lease_owner),
                )
            return cursor.rowcount == 1

    def extend_lease_for_test(self, job_id: UUID, expires_at: datetime) -> None:
        with self._connection() as connection:
            connection.execute(
                "UPDATE ocr_jobs SET lease_expires_at = %s WHERE id = %s",
                (expires_at.astimezone(UTC).replace(tzinfo=None), job_id),
            )

    @contextmanager
    def _connection(self) -> Iterator[psycopg.Connection]:
        with self._pool.connection() as connection:
            connection.row_factory = dict_row
            with connection.transaction():
                yield connection
