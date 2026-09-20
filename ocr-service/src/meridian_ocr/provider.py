from __future__ import annotations

from typing import Protocol

from .models import OcrProviderResult


class OcrProvider(Protocol):
    def is_ready(self) -> bool: ...

    def process(
        self,
        document_bytes: bytes,
        mime_type: str,
        trace_id: str,
    ) -> OcrProviderResult: ...
