from pathlib import Path
from unittest.mock import Mock

from fastapi.testclient import TestClient

from meridian_ocr.app import Runtime, create_app
from meridian_ocr.config import Settings


def settings() -> Settings:
    return Settings(
        database_url="postgresql://unused",
        document_storage_root=Path("/unused"),
        encryption_key="",
        worker_id="test",
        lease_seconds=30,
        max_attempts=3,
        initial_backoff_seconds=1,
        polling_interval_seconds=0.01,
        confidence_threshold=0.85,
        google_project="project",
        google_location="us",
        google_processor_id="processor",
    )


def test_health_is_alive_and_ready_is_purpose_limited() -> None:
    repository = Mock()
    repository.close.return_value = None
    provider = Mock()
    worker = Mock()
    worker.is_ready.return_value = True
    runtime = Runtime(settings(), repository, provider, worker)

    with TestClient(create_app(runtime, start_worker=False)) as client:
        assert client.get("/health").json() == {"status": "alive"}
        response = client.get("/ready")
        assert response.status_code == 200
        assert response.json() == {"status": "ready"}
        assert client.get("/docs").status_code == 404


def test_not_ready_returns_only_controlled_status() -> None:
    repository = Mock()
    repository.close.return_value = None
    runtime = Runtime(settings(), repository, Mock(), None)

    with TestClient(create_app(runtime, start_worker=False)) as client:
        response = client.get("/ready")

    assert response.status_code == 503
    assert response.json() == {"status": "not-ready"}
