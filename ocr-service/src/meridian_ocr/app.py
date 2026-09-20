from __future__ import annotations

import asyncio
from contextlib import asynccontextmanager, suppress
from dataclasses import dataclass
from typing import AsyncIterator

from fastapi import FastAPI, Response, status

from .config import Settings
from .crypto import OcrResultCipher
from .google_provider import GoogleDocumentAiProvider
from .intake_extractor import IntakeFieldExtractor
from .repository import OcrJobRepository
from .storage import DocumentObjectStore
from .worker import OcrWorker


@dataclass
class Runtime:
    settings: Settings
    repository: OcrJobRepository
    provider: GoogleDocumentAiProvider
    worker: OcrWorker | None

    @classmethod
    def from_settings(cls, settings: Settings) -> "Runtime":
        repository = OcrJobRepository(settings.database_url)
        provider = GoogleDocumentAiProvider(
            settings.google_project,
            settings.google_location,
            settings.google_processor_id,
        )
        worker = None
        try:
            cipher = OcrResultCipher(settings.encryption_key)
            worker = OcrWorker(
                repository,
                DocumentObjectStore(settings.document_storage_root),
                provider,
                IntakeFieldExtractor(),
                cipher,
                settings.worker_id,
                settings.lease_seconds,
                settings.max_attempts,
                settings.initial_backoff_seconds,
                settings.confidence_threshold,
            )
        except ValueError:
            pass
        return cls(settings, repository, provider, worker)

    def ready(self) -> bool:
        return self.worker is not None and self.worker.is_ready()


def create_app(runtime: Runtime | None = None, start_worker: bool = True) -> FastAPI:
    resolved = runtime or Runtime.from_settings(Settings.from_environment())

    @asynccontextmanager
    async def lifespan(_: FastAPI) -> AsyncIterator[None]:
        task = None
        if start_worker and resolved.worker is not None:
            task = asyncio.create_task(_run_worker(resolved))
        try:
            yield
        finally:
            if task is not None:
                task.cancel()
                with suppress(asyncio.CancelledError):
                    await task
            resolved.repository.close()

    app = FastAPI(title="Meridian OCR Service", docs_url=None, redoc_url=None, lifespan=lifespan)
    app.state.runtime = resolved

    @app.get("/health")
    def health() -> dict[str, str]:
        return {"status": "alive"}

    @app.get("/ready")
    def ready(response: Response) -> dict[str, str]:
        if not resolved.ready():
            response.status_code = status.HTTP_503_SERVICE_UNAVAILABLE
            return {"status": "not-ready"}
        return {"status": "ready"}

    return app


async def _run_worker(runtime: Runtime) -> None:
    assert runtime.worker is not None
    while True:
        try:
            processed = await asyncio.to_thread(runtime.worker.run_once)
        except Exception:
            processed = False
        if not processed:
            await asyncio.sleep(runtime.settings.polling_interval_seconds)
