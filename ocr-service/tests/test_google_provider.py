from unittest.mock import Mock

import pytest
from google.cloud import documentai

from meridian_ocr.google_provider import GoogleDocumentAiProvider


def test_maps_google_document_to_provider_neutral_result() -> None:
    client = Mock()
    document = documentai.Document(
        text="Xin chao Meridian",
        pages=[
            documentai.Document.Page(
                page_number=1,
                dimension=documentai.Document.Page.Dimension(width=800, height=1200, unit="px"),
                lines=[
                    documentai.Document.Page.Line(
                        layout=documentai.Document.Page.Layout(
                            text_anchor=documentai.Document.TextAnchor(
                                text_segments=[documentai.Document.TextAnchor.TextSegment(end_index=8)]
                            ),
                            confidence=0.96,
                        )
                    )
                ],
                tokens=[],
            )
        ],
    )
    client.process_document.return_value = documentai.ProcessResponse(document=document)
    provider = GoogleDocumentAiProvider("project", "us", "processor", client=client)

    result = provider.process(b"%PDF-test", "application/pdf", "CUSTOMER_IDENTITY", "trace")

    assert result.provider == "GOOGLE_DOCUMENT_AI"
    assert result.processor_name == "Enterprise Document OCR"
    assert result.processor_version is None
    assert result.extracted_text == "Xin chao Meridian"
    assert result.normalized_layout["pages"][0]["lines"][0]["text"] == "Xin chao"
    assert result.confidence == pytest.approx(0.96)
    assert result.structured_suggestions == []
    request = client.process_document.call_args.kwargs["request"]
    assert request.name == "projects/project/locations/us/processors/processor"


def test_missing_configuration_is_not_ready_and_makes_no_network_call() -> None:
    provider = GoogleDocumentAiProvider("", "", "")

    assert provider.is_ready() is False
