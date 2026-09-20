from __future__ import annotations

import time
from collections.abc import Iterable
from typing import Any

from google.api_core import exceptions as google_exceptions
from google.cloud import documentai

from .models import FailureCategory, OcrProcessingError, OcrProviderResult


class GoogleDocumentAiProvider:
    PROVIDER = "GOOGLE_DOCUMENT_AI"
    PROCESSOR_MODEL = "Enterprise Document OCR"

    def __init__(
        self,
        project: str,
        location: str,
        processor_id: str,
        client: documentai.DocumentProcessorServiceClient | None = None,
    ) -> None:
        self._project = project.strip()
        self._location = location.strip()
        self._processor_id = processor_id.strip()
        self._client = client
        if self._client is None and self._configured:
            endpoint = f"{self._location}-documentai.googleapis.com"
            try:
                self._client = documentai.DocumentProcessorServiceClient(
                    client_options={"api_endpoint": endpoint}
                )
            except Exception:
                self._client = None

    @property
    def _configured(self) -> bool:
        return bool(self._project and self._location and self._processor_id)

    @property
    def processor_resource_name(self) -> str:
        return (
            f"projects/{self._project}/locations/{self._location}/processors/"
            f"{self._processor_id}"
        )

    def is_ready(self) -> bool:
        return self._configured and self._client is not None

    def process(
        self,
        document_bytes: bytes,
        mime_type: str,
        trace_id: str,
    ) -> OcrProviderResult:
        del trace_id
        if not self.is_ready():
            raise OcrProcessingError(FailureCategory.PROVIDER_UNAVAILABLE, retryable=True)
        request = documentai.ProcessRequest(
            name=self.processor_resource_name,
            raw_document=documentai.RawDocument(content=document_bytes, mime_type=mime_type),
        )
        started = time.monotonic()
        try:
            response = self._client.process_document(request=request)
        except (google_exceptions.TooManyRequests, google_exceptions.ServiceUnavailable,
                google_exceptions.DeadlineExceeded) as exc:
            raise OcrProcessingError(FailureCategory.PROVIDER_UNAVAILABLE, retryable=True) from exc
        except (google_exceptions.InvalidArgument, google_exceptions.FailedPrecondition) as exc:
            raise OcrProcessingError(FailureCategory.PROVIDER_REJECTED, retryable=False) from exc
        except google_exceptions.GoogleAPICallError as exc:
            raise OcrProcessingError(FailureCategory.PROVIDER_ERROR, retryable=True) from exc
        document = response.document
        confidence_values: list[float] = []
        pages = [self._page(page, document.text, confidence_values) for page in document.pages]
        confidence = (
            sum(confidence_values) / len(confidence_values) if confidence_values else None
        )
        return OcrProviderResult(
            extracted_text=document.text or "",
            normalized_layout={"pages": pages},
            confidence=confidence,
            provider=self.PROVIDER,
            processor_name=self.PROCESSOR_MODEL,
            processor_version=None,
            processing_duration_ms=max(0, round((time.monotonic() - started) * 1000)),
        )

    def _page(self, page: Any, full_text: str, confidences: list[float]) -> dict[str, Any]:
        return {
            "pageNumber": getattr(page, "page_number", None),
            "width": getattr(getattr(page, "dimension", None), "width", None),
            "height": getattr(getattr(page, "dimension", None), "height", None),
            "unit": getattr(getattr(page, "dimension", None), "unit", None),
            "lines": [self._layout_item(line, full_text, confidences) for line in page.lines],
            "tokens": [self._layout_item(token, full_text, confidences) for token in page.tokens],
        }

    def _layout_item(
        self, item: Any, full_text: str, confidences: list[float]
    ) -> dict[str, Any]:
        layout = item.layout
        confidence = float(layout.confidence) if layout.confidence is not None else None
        if confidence is not None:
            confidences.append(confidence)
        vertices = getattr(getattr(layout, "bounding_poly", None), "normalized_vertices", [])
        return {
            "text": _text_for(layout.text_anchor, full_text),
            "confidence": confidence,
            "boundingPolygon": [
                {"x": float(getattr(vertex, "x", 0)), "y": float(getattr(vertex, "y", 0))}
                for vertex in vertices
            ],
        }


def _text_for(text_anchor: Any, full_text: str) -> str:
    segments: Iterable[Any] = getattr(text_anchor, "text_segments", [])
    return "".join(
        full_text[int(getattr(segment, "start_index", 0)) : int(segment.end_index)]
        for segment in segments
    )
