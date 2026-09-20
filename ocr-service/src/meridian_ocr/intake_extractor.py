from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class IntakeFieldSuggestion:
    field_name: str
    proposed_value: str
    confidence: float | None

    def as_dict(self) -> dict[str, Any]:
        return {
            "fieldName": self.field_name,
            "proposedValue": self.proposed_value,
            "confidence": self.confidence,
        }


class IntakeFieldExtractor:
    _COMMON_LABELS = {
        "fullName": ("full name", "ho va ten", "ho ten"),
        "identityReference": ("identity reference", "cccd", "so cccd", "citizen id"),
        "phoneNumber": ("phone number", "phone", "so dien thoai"),
        "residentialAddress": ("residential address", "address", "dia chi thuong tru", "dia chi"),
        "employmentStatus": ("employment status", "tinh trang viec lam"),
        "employerName": ("employer name", "employer", "ten don vi cong tac"),
        "bankCode": ("bank code", "ma ngan hang"),
        "bankNameSnapshot": ("bank name", "ten ngan hang"),
        "accountHolderName": ("account holder name", "account holder", "chu tai khoan"),
        "accountNumber": ("account number", "so tai khoan"),
        "requestedAmount": ("requested amount", "loan amount", "so tien de nghi vay"),
        "requestedTermMonths": ("requested term months", "loan term months", "thoi han vay thang"),
    }
    _COLLATERAL_LABELS = {
        "collateral.type": ("collateral type", "loai tai san bao dam"),
        "collateral.description": ("collateral description", "mo ta tai san"),
        "collateral.estimatedValue": ("estimated value", "gia tri uoc tinh"),
        "collateral.ownershipStatus": ("ownership status", "tinh trang so huu"),
        "collateral.conditionNote": ("condition note", "tinh trang tai san"),
    }
    _IDENTITY_FIELDS = {"fullName", "identityReference"}

    def extract(self, evidence_type: str, normalized_layout: dict[str, Any]) -> list[dict[str, Any]]:
        labels = dict(self._COMMON_LABELS)
        if evidence_type == "CUSTOMER_IDENTITY":
            labels = {name: aliases for name, aliases in labels.items() if name in self._IDENTITY_FIELDS}
        elif evidence_type == "COLLATERAL_PAPER_APPLICATION":
            labels.update(self._COLLATERAL_LABELS)
        elif evidence_type != "UCL_PAPER_APPLICATION":
            return []

        candidates: dict[str, dict[str, IntakeFieldSuggestion]] = {
            field_name: {} for field_name in labels
        }
        for line in self._lines(normalized_layout):
            text = str(line.get("text") or "").strip()
            normalized = _normalize(text)
            for field_name, aliases in labels.items():
                value = _labeled_value(text, normalized, aliases)
                if value and self._credible(field_name, value):
                    suggestion = IntakeFieldSuggestion(
                        field_name, value, _confidence(line.get("confidence"))
                    )
                    existing = candidates[field_name].get(value)
                    if existing is None or _confidence_rank(
                        suggestion.confidence
                    ) > _confidence_rank(existing.confidence):
                        candidates[field_name][value] = suggestion
        suggestions = [
            next(iter(values.values()))
            for values in candidates.values()
            if len(values) == 1
        ]
        return [suggestion.as_dict() for suggestion in suggestions]

    @staticmethod
    def _lines(layout: dict[str, Any]):
        for page in layout.get("pages", []):
            for line in page.get("lines", []):
                yield line

    @staticmethod
    def _credible(field_name: str, value: str) -> bool:
        if len(value) > 1000:
            return False
        if field_name == "identityReference":
            return bool(re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9 .\-/]{5,39}", value))
        if field_name == "phoneNumber":
            return bool(re.fullmatch(r"\+?[0-9][0-9 .-]{7,19}", value))
        if field_name in {"requestedAmount", "requestedTermMonths", "collateral.estimatedValue"}:
            return bool(re.fullmatch(r"[0-9][0-9., ]*", value))
        if field_name == "collateral.type":
            return value.upper().replace(" ", "_") in {
                "MOTORBIKE", "CAR", "ELECTRONICS", "PROPERTY_DOCUMENT", "OTHER"
            }
        return len(value) >= 2


def _normalize(value: str) -> str:
    decomposed = unicodedata.normalize("NFD", value.casefold())
    return " ".join("".join(ch for ch in decomposed if unicodedata.category(ch) != "Mn").split())


def _labeled_value(original: str, normalized: str, aliases: tuple[str, ...]) -> str | None:
    for alias in sorted(aliases, key=len, reverse=True):
        match = re.match(rf"^{re.escape(alias)}\s*[:\-]\s*(.+)$", normalized)
        if not match:
            continue
        separator = re.search(r"[:\-]", original)
        if separator:
            value = original[separator.end():].strip()
            return value or None
    return None


def _confidence(value: Any) -> float | None:
    if isinstance(value, (int, float)) and 0 <= float(value) <= 1:
        return float(value)
    return None


def _confidence_rank(value: float | None) -> float:
    return value if value is not None else -1.0
