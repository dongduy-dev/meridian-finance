from __future__ import annotations

import os
from collections.abc import Iterator
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit
from uuid import uuid4

import psycopg
import pytest

from meridian_ocr.repository import OcrJobRepository


@pytest.fixture
def database_repository() -> Iterator[tuple[OcrJobRepository, str]]:
    base_url = os.getenv("OCR_TEST_DATABASE_URL")
    if not base_url:
        pytest.skip("OCR_TEST_DATABASE_URL is required for PostgreSQL integration tests")
    schema = "ocr_test_" + uuid4().hex
    with psycopg.connect(base_url, autocommit=True) as connection:
        connection.execute(f'CREATE SCHEMA "{schema}"')
        connection.execute(f'SET search_path TO "{schema}"')
        _create_tables(connection)
    repository = OcrJobRepository(_with_search_path(base_url, schema))
    try:
        yield repository, schema
    finally:
        repository.close()
        with psycopg.connect(base_url, autocommit=True) as connection:
            connection.execute(f'DROP SCHEMA IF EXISTS "{schema}" CASCADE')


def insert_job(database_url: str, schema: str, **overrides: object) -> dict[str, object]:
    values: dict[str, object] = {
        "id": uuid4(),
        "intake_document_version_id": uuid4(),
        "evidence_type": "CUSTOMER_IDENTITY",
        "source_storage_key": "ab/assigned-object",
        "source_mime_type": "application/pdf",
        "source_sha256_hex": "a" * 64,
        "state": "PENDING",
        "lease_owner": None,
        "lease_expires_at": None,
        "attempt_count": 0,
        "failure_category": None,
        "trace_id": uuid4(),
    }
    values.update(overrides)
    with psycopg.connect(database_url, autocommit=True) as connection:
        connection.execute(f'SET search_path TO "{schema}"')
        connection.execute(
            """
            INSERT INTO ocr_jobs (
                id, intake_document_version_id, evidence_type, source_storage_key,
                source_mime_type, source_sha256_hex, state, lease_owner,
                lease_expires_at, attempt_count, next_attempt_at, failure_category,
                trace_id, created_at, updated_at
            ) VALUES (
                %(id)s, %(intake_document_version_id)s, %(evidence_type)s,
                %(source_storage_key)s, %(source_mime_type)s, %(source_sha256_hex)s,
                %(state)s, %(lease_owner)s, %(lease_expires_at)s, %(attempt_count)s,
                CURRENT_TIMESTAMP, %(failure_category)s, %(trace_id)s,
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
            )
            """,
            values,
        )
    return values


def _with_search_path(database_url: str, schema: str) -> str:
    parts = urlsplit(database_url)
    query = dict(parse_qsl(parts.query))
    query["options"] = f"-csearch_path={schema}"
    return urlunsplit((parts.scheme, parts.netloc, parts.path, urlencode(query), parts.fragment))


def _create_tables(connection: psycopg.Connection) -> None:
    connection.execute(
        """
        CREATE TABLE ocr_jobs (
            id UUID PRIMARY KEY,
            intake_document_version_id UUID UNIQUE NOT NULL,
            evidence_type VARCHAR(50) NOT NULL,
            source_storage_key VARCHAR(255) NOT NULL,
            source_mime_type VARCHAR(100) NOT NULL,
            source_sha256_hex VARCHAR(64) NOT NULL,
            state VARCHAR(20) NOT NULL,
            lease_owner VARCHAR(120),
            lease_expires_at TIMESTAMP WITHOUT TIME ZONE,
            attempt_count INTEGER NOT NULL,
            next_attempt_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
            failure_category VARCHAR(50),
            trace_id UUID NOT NULL,
            created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
            updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
            completed_at TIMESTAMP WITHOUT TIME ZONE,
            failed_at TIMESTAMP WITHOUT TIME ZONE
        );
        CREATE INDEX idx_ocr_jobs_pending_claim ON ocr_jobs (next_attempt_at, created_at, id)
            WHERE state = 'PENDING';
        CREATE INDEX idx_ocr_jobs_expired_lease ON ocr_jobs (lease_expires_at, created_at, id)
            WHERE state = 'PROCESSING';
        CREATE TABLE ocr_results (
            id UUID PRIMARY KEY,
            ocr_job_id UUID UNIQUE NOT NULL REFERENCES ocr_jobs(id),
            provider VARCHAR(80) NOT NULL,
            processor_name VARCHAR(255) NOT NULL,
            processor_version VARCHAR(120),
            encrypted_extracted_text TEXT NOT NULL,
            encrypted_normalized_payload TEXT NOT NULL,
            encrypted_structured_suggestions TEXT NOT NULL,
            normalized_confidence NUMERIC(5, 4),
            disposition VARCHAR(30) NOT NULL,
            processing_duration_ms BIGINT NOT NULL,
            created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
        )
        """
    )
