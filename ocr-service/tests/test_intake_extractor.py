from meridian_ocr.intake_extractor import IntakeFieldExtractor


def _line(
    text: str,
    confidence: float,
    left: float,
    top: float,
    right: float,
    bottom: float,
) -> dict:
    return {
        "text": text,
        "confidence": confidence,
        "boundingPolygon": [
            {"x": left, "y": top},
            {"x": right, "y": top},
            {"x": right, "y": bottom},
            {"x": left, "y": bottom},
        ],
    }


def _layout(*lines: dict) -> dict:
    return {"pages": [{"lines": list(lines)}]}


def _text_layout(*lines: tuple[str, float]) -> dict:
    return {"pages": [{"lines": [
        {"text": text, "confidence": confidence} for text, confidence in lines
    ]}]}


def _by_name(suggestions: list[dict]) -> dict[str, dict]:
    return {item["fieldName"]: item for item in suggestions}


def test_extracts_actual_ucl_form_geometry_and_propagates_confidence() -> None:
    result = _by_name(IntakeFieldExtractor().extract("UCL_PAPER_APPLICATION", _layout(
        _line("Customer name", 0.99, 0.08, 0.10, 0.25, 0.12),
        _line("Nguyen Van An", 0.98, 0.31, 0.10, 0.55, 0.12),
        _line("CCCD", 0.99, 0.08, 0.16, 0.16, 0.18),
        _line("012345678901", 0.97, 0.31, 0.16, 0.49, 0.18),
        _line("Phone number", 0.98, 0.08, 0.22, 0.23, 0.24),
        _line("0901234567", 0.96, 0.31, 0.22, 0.46, 0.24),
        _line("Residential address", 0.98, 0.08, 0.28, 0.27, 0.30),
        _line("1 Meridian Street", 0.95, 0.31, 0.28, 0.58, 0.30),
        _line("Employment status", 0.98, 0.08, 0.34, 0.27, 0.36),
        _line("EMPLOYED", 0.94, 0.31, 0.34, 0.44, 0.36),
        _line("Employer name (if applicable)", 0.97, 0.08, 0.40, 0.31, 0.42),
        _line("Meridian Workshop", 0.93, 0.31, 0.44, 0.58, 0.46),
        _line("Bank code", 0.98, 0.08, 0.50, 0.19, 0.52),
        _line("MER", 0.92, 0.31, 0.50, 0.37, 0.52),
        _line("Bank name", 0.98, 0.08, 0.56, 0.19, 0.58),
        _line("Meridian Bank", 0.91, 0.31, 0.56, 0.52, 0.58),
        _line("Account holder name", 0.98, 0.08, 0.62, 0.27, 0.64),
        _line("NGUYEN VAN AN", 0.90, 0.31, 0.62, 0.54, 0.64),
        _line("Account number", 0.98, 0.08, 0.68, 0.24, 0.70),
        _line("1234567890", 0.89, 0.31, 0.68, 0.47, 0.70),
        _line("Requested amount (VND)", 0.98, 0.08, 0.74, 0.29, 0.76),
        _line("10000000", 0.88, 0.08, 0.78, 0.23, 0.80),
        _line("Requested term", 0.98, 0.08, 0.84, 0.23, 0.86),
        _line("☒ 12 months", 0.87, 0.31, 0.84, 0.46, 0.86),
        _line("Terms consent accepted", 0.99, 0.08, 0.90, 0.28, 0.92),
        _line("Yes", 0.99, 0.31, 0.90, 0.37, 0.92),
    )))

    assert result["fullName"]["proposedValue"] == "Nguyen Van An"
    assert result["requestedAmount"]["proposedValue"] == "10000000"
    assert result["requestedAmount"]["confidence"] == 0.88
    assert result["requestedTermMonths"]["proposedValue"] == "12"
    assert "termsConsentAccepted" not in result
    assert "dataProcessingConsentAccepted" not in result


def test_extracts_actual_collateral_form_geometry_without_guessing_checkboxes() -> None:
    result = _by_name(IntakeFieldExtractor().extract(
        "COLLATERAL_PAPER_APPLICATION",
        _layout(
            _line("Requested amount (VND)", 0.97, 0.08, 0.10, 0.29, 0.12),
            _line("25000000", 0.91, 0.34, 0.10, 0.48, 0.12),
            _line("Requested term", 0.97, 0.08, 0.18, 0.23, 0.20),
            _line("6 months  12 months  18 months  24 months", 0.93, 0.30, 0.18, 0.77, 0.20),
            _line("Collateral type", 0.98, 0.08, 0.26, 0.22, 0.28),
            _line("☒ Motorbike", 0.94, 0.30, 0.26, 0.45, 0.28),
            _line("☐ Car", 0.93, 0.50, 0.26, 0.59, 0.28),
            _line("Description", 0.97, 0.08, 0.34, 0.19, 0.36),
            _line("Meridian test motorbike", 0.88, 0.08, 0.38, 0.43, 0.40),
            _line("Customer-estimated value (VND)", 0.98, 0.08, 0.46, 0.35, 0.48),
            _line("35000000", 0.90, 0.40, 0.46, 0.54, 0.48),
            _line("Ownership status", 0.97, 0.08, 0.54, 0.24, 0.56),
            _line("OWNED", 0.86, 0.30, 0.54, 0.41, 0.56),
            _line("Condition note", 0.97, 0.08, 0.62, 0.22, 0.64),
            _line("Normal used condition", 0.84, 0.08, 0.66, 0.37, 0.68),
        ),
    ))

    assert set(result) == {
        "requestedAmount",
        "collateral.type",
        "collateral.description",
        "collateral.estimatedValue",
        "collateral.ownershipStatus",
        "collateral.conditionNote",
    }
    assert result["collateral.type"]["proposedValue"] == "MOTORBIKE"
    assert result["collateral.description"]["proposedValue"] == "Meridian test motorbike"
    assert result["collateral.estimatedValue"]["proposedValue"] == "35000000"
    assert "requestedTermMonths" not in result


def test_ambiguous_spatial_values_are_omitted() -> None:
    result = _by_name(IntakeFieldExtractor().extract("UCL_PAPER_APPLICATION", _layout(
        _line("Requested amount (VND)", 0.98, 0.08, 0.10, 0.29, 0.12),
        _line("10000000", 0.94, 0.34, 0.10, 0.48, 0.12),
        _line("12000000", 0.93, 0.08, 0.14, 0.22, 0.16),
        _line("Phone number", 0.98, 0.08, 0.24, 0.23, 0.26),
        _line("0901234567", 0.92, 0.31, 0.24, 0.46, 0.26),
    )))

    assert "requestedAmount" not in result
    assert result["phoneNumber"]["proposedValue"] == "0901234567"


def test_unmarked_checkbox_values_are_not_suggestions() -> None:
    result = IntakeFieldExtractor().extract("COLLATERAL_PAPER_APPLICATION", _layout(
        _line("Requested term", 0.98, 0.08, 0.10, 0.23, 0.12),
        _line("12 months", 0.97, 0.30, 0.10, 0.43, 0.12),
        _line("Collateral type", 0.98, 0.08, 0.18, 0.22, 0.20),
        _line("MOTORBIKE", 0.97, 0.30, 0.18, 0.45, 0.20),
    ))

    assert result == []


def test_label_value_parsing_remains_a_fallback() -> None:
    result = _by_name(IntakeFieldExtractor().extract("UCL_PAPER_APPLICATION", _text_layout(
        ("Customer name: Nguyen Thi B", 0.97),
        ("Bank code: MER", 0.96),
        ("Requested amount (VND): 10000000", 0.95),
        ("Requested term: 12", 0.94),
        ("Consent accepted: Yes", 0.99),
    )))

    assert result["fullName"]["proposedValue"] == "Nguyen Thi B"
    assert result["bankCode"]["proposedValue"] == "MER"
    assert result["requestedAmount"]["proposedValue"] == "10000000"
    assert result["requestedTermMonths"]["proposedValue"] == "12"
    assert all("consent" not in field_name.casefold() for field_name in result)


def test_provider_neutral_layout_requires_no_provider_metadata() -> None:
    result = _by_name(IntakeFieldExtractor().extract("CUSTOMER_IDENTITY", _layout(
        _line("Full name", 0.97, 0.08, 0.10, 0.20, 0.12),
        _line("Nguyen Thi B", 0.96, 0.28, 0.10, 0.48, 0.12),
        _line("So CCCD", 0.95, 0.08, 0.18, 0.18, 0.20),
        _line("079123456789", 0.94, 0.28, 0.18, 0.46, 0.20),
        _line("Que quan", 0.93, 0.08, 0.32, 0.18, 0.34),
        _line("Somewhere", 0.92, 0.28, 0.32, 0.43, 0.34),
    )))

    assert set(result) == {"fullName", "identityReference"}


def test_missing_unlabeled_or_invalid_values_are_not_invented() -> None:
    result = IntakeFieldExtractor().extract("UCL_PAPER_APPLICATION", _text_layout(
        ("Requested amount or term unclear", 0.95),
        ("Phone number: unclear", 0.90),
        ("CCCD:", 0.99),
        ("12 months", 0.99),
    ))
    assert result == []

    ambiguous = _by_name(IntakeFieldExtractor().extract("UCL_PAPER_APPLICATION", _text_layout(
        ("Full name: Nguyen Van An", 0.95),
        ("Full name: Nguyen Thi B", 0.94),
        ("Requested term months: 12", 0.93),
    )))
    assert "fullName" not in ambiguous
    assert ambiguous["requestedTermMonths"]["proposedValue"] == "12"
