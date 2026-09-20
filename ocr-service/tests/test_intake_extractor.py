from meridian_ocr.intake_extractor import IntakeFieldExtractor


def _layout(*lines: tuple[str, float]) -> dict:
    return {"pages": [{"lines": [
        {"text": text, "confidence": confidence} for text, confidence in lines
    ]}]}


def _by_name(suggestions: list[dict]) -> dict[str, dict]:
    return {item["fieldName"]: item for item in suggestions}


def test_extracts_controlled_ucl_form_and_propagates_confidence() -> None:
    result = _by_name(IntakeFieldExtractor().extract("UCL_PAPER_APPLICATION", _layout(
        ("Full name: Nguyen Van An", 0.98),
        ("CCCD: 012345678901", 0.97),
        ("Phone number: 0901234567", 0.96),
        ("Residential address: 1 Meridian Street", 0.95),
        ("Employment status: EMPLOYED", 0.94),
        ("Employer name: Meridian Workshop", 0.93),
        ("Bank code: MER", 0.92),
        ("Bank name: Meridian Bank", 0.91),
        ("Account holder: NGUYEN VAN AN", 0.90),
        ("Account number: 1234567890", 0.89),
        ("Requested amount: 10000000", 0.88),
        ("Requested term months: 12", 0.87),
        ("Terms consent accepted: Yes", 0.99),
    )))

    assert result["fullName"]["proposedValue"] == "Nguyen Van An"
    assert result["fullName"]["confidence"] == 0.98
    assert result["requestedTermMonths"]["proposedValue"] == "12"
    assert "termsConsentAccepted" not in result
    assert "dataProcessingConsentAccepted" not in result


def test_extracts_controlled_collateral_fields() -> None:
    result = _by_name(IntakeFieldExtractor().extract(
        "COLLATERAL_PAPER_APPLICATION",
        _layout(
            ("Requested amount: 25000000", 0.91),
            ("Collateral type: MOTORBIKE", 0.94),
            ("Collateral description: Meridian test motorbike", 0.88),
            ("Estimated value: 35000000", 0.90),
            ("Ownership status: OWNED", 0.86),
            ("Condition note: Normal used condition", 0.84),
        ),
    ))

    assert set(result) == {
        "requestedAmount", "collateral.type", "collateral.description",
        "collateral.estimatedValue", "collateral.ownershipStatus",
        "collateral.conditionNote",
    }


def test_identity_extraction_is_conservative() -> None:
    result = _by_name(IntakeFieldExtractor().extract("CUSTOMER_IDENTITY", _layout(
        ("Ho va ten: Nguyen Thi B", 0.97),
        ("So CCCD: 079123456789", 0.96),
        ("Que quan: Somewhere", 0.95),
    )))
    assert set(result) == {"fullName", "identityReference"}


def test_missing_ambiguous_or_unlabeled_values_are_not_invented() -> None:
    result = IntakeFieldExtractor().extract("UCL_PAPER_APPLICATION", _layout(
        ("Requested amount or term unclear", 0.95),
        ("Phone number: unclear", 0.90),
        ("CCCD:", 0.99),
        ("12 months", 0.99),
    ))
    assert result == []

    ambiguous = _by_name(IntakeFieldExtractor().extract("UCL_PAPER_APPLICATION", _layout(
        ("Full name: Nguyen Van An", 0.95),
        ("Full name: Nguyen Thi B", 0.94),
        ("Requested term months: 12", 0.93),
    )))
    assert "fullName" not in ambiguous
    assert ambiguous["requestedTermMonths"]["proposedValue"] == "12"
