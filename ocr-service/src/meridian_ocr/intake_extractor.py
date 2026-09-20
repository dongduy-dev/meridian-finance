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


@dataclass(frozen=True)
class _Bounds:
    left: float
    top: float
    right: float
    bottom: float

    @property
    def center_y(self) -> float:
        return (self.top + self.bottom) / 2

    @property
    def height(self) -> float:
        return self.bottom - self.top


@dataclass(frozen=True)
class _Line:
    index: int
    page_index: int
    text: str
    normalized: str
    confidence: float | None
    bounds: _Bounds | None


class IntakeFieldExtractor:
    _COMMON_LABELS = {
        "fullName": ("customer full name", "customer name", "full name", "ho va ten", "ho ten"),
        "identityReference": ("identity reference", "cccd", "so cccd", "citizen id"),
        "phoneNumber": ("phone number", "phone", "so dien thoai"),
        "residentialAddress": ("residential address", "address", "dia chi thuong tru", "dia chi"),
        "employmentStatus": ("employment status", "tinh trang viec lam"),
        "employerName": (
            "employer name (if applicable)", "employer name", "employer", "ten don vi cong tac"
        ),
        "bankCode": ("bank code", "ma ngan hang"),
        "bankNameSnapshot": ("bank name", "ten ngan hang"),
        "accountHolderName": ("account holder name", "account holder", "chu tai khoan"),
        "accountNumber": ("account number", "so tai khoan"),
        "requestedAmount": (
            "requested amount (vnd)", "requested amount", "loan amount", "so tien de nghi vay"
        ),
        "requestedTermMonths": (
            "requested term", "requested term months", "loan term months", "thoi han vay thang"
        ),
    }
    _COLLATERAL_LABELS = {
        "collateral.type": ("collateral type", "loai tai san bao dam"),
        "collateral.description": ("description", "collateral description", "mo ta tai san"),
        "collateral.estimatedValue": (
            "customer-estimated value (vnd)", "customer estimated value (vnd)",
            "estimated value", "gia tri uoc tinh"
        ),
        "collateral.ownershipStatus": ("ownership status", "tinh trang so huu"),
        "collateral.conditionNote": ("condition note", "tinh trang tai san"),
    }
    _IDENTITY_FIELDS = {"fullName", "identityReference"}
    _SELECTION_FIELDS = {"requestedTermMonths", "collateral.type"}

    def extract(self, evidence_type: str, normalized_layout: dict[str, Any]) -> list[dict[str, Any]]:
        labels = dict(self._COMMON_LABELS)
        if evidence_type == "CUSTOMER_IDENTITY":
            labels = {name: aliases for name, aliases in labels.items() if name in self._IDENTITY_FIELDS}
        elif evidence_type == "COLLATERAL_PAPER_APPLICATION":
            labels.update(self._COLLATERAL_LABELS)
        elif evidence_type != "UCL_PAPER_APPLICATION":
            return []

        lines = list(self._lines(normalized_layout))
        spatial, ambiguous = self._spatial_candidates(lines, labels)
        candidates: dict[str, dict[str, IntakeFieldSuggestion]] = {
            field_name: dict(spatial.get(field_name, {})) for field_name in labels
        }

        # Colon-delimited text remains a fallback for layouts without one clear spatial match.
        for field_name, aliases in labels.items():
            if candidates[field_name] or field_name in ambiguous:
                continue
            for line in lines:
                value = _labeled_value(line.text, line.normalized, aliases)
                if value and self._credible(field_name, value):
                    suggestion = IntakeFieldSuggestion(field_name, value, line.confidence)
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

    def _spatial_candidates(
        self, lines: list[_Line], labels: dict[str, tuple[str, ...]]
    ) -> tuple[dict[str, dict[str, IntakeFieldSuggestion]], set[str]]:
        label_lines: list[tuple[_Line, str]] = []
        label_indexes: set[int] = set()
        for line in lines:
            field_name = _exact_label_field(line.normalized, labels)
            if field_name is not None:
                label_lines.append((line, field_name))
                label_indexes.add(line.index)

        accepted: list[tuple[str, _Line, IntakeFieldSuggestion]] = []
        ambiguous: set[str] = set()
        for label_line, field_name in label_lines:
            if label_line.bounds is None:
                continue
            matches: list[tuple[_Line, str]] = []
            for value_line in lines:
                if (
                    value_line.index in label_indexes
                    or value_line.page_index != label_line.page_index
                    or value_line.bounds is None
                    or not _is_nearby_value(label_line.bounds, value_line.bounds)
                ):
                    continue
                value = self._spatial_value(field_name, value_line.text)
                if value is not None and self._credible(field_name, value):
                    matches.append((value_line, value))
            if len(matches) != 1:
                if matches:
                    ambiguous.add(field_name)
                continue
            value_line, value = matches[0]
            accepted.append((
                field_name,
                value_line,
                IntakeFieldSuggestion(
                    field_name,
                    value,
                    _combined_confidence(label_line.confidence, value_line.confidence),
                ),
            ))

        claims: dict[int, set[str]] = {}
        for field_name, value_line, _ in accepted:
            claims.setdefault(value_line.index, set()).add(field_name)
        for claimed_fields in claims.values():
            if len(claimed_fields) > 1:
                ambiguous.update(claimed_fields)

        candidates: dict[str, dict[str, IntakeFieldSuggestion]] = {}
        for field_name, value_line, suggestion in accepted:
            if field_name in ambiguous or len(claims[value_line.index]) > 1:
                continue
            existing = candidates.setdefault(field_name, {}).get(suggestion.proposed_value)
            if existing is None or _confidence_rank(
                suggestion.confidence
            ) > _confidence_rank(existing.confidence):
                candidates[field_name][suggestion.proposed_value] = suggestion
        return candidates, ambiguous

    @staticmethod
    def _lines(layout: dict[str, Any]):
        index = 0
        for page_index, page in enumerate(layout.get("pages", [])):
            for raw_line in page.get("lines", []):
                text = str(raw_line.get("text") or "").strip()
                yield _Line(
                    index=index,
                    page_index=page_index,
                    text=text,
                    normalized=_normalize(text),
                    confidence=_confidence(raw_line.get("confidence")),
                    bounds=_bounds(raw_line.get("boundingPolygon")),
                )
                index += 1

    @classmethod
    def _spatial_value(cls, field_name: str, value: str) -> str | None:
        cleaned = value.strip().strip("_").strip()
        if not cleaned:
            return None
        if field_name not in cls._SELECTION_FIELDS:
            return cleaned
        return _reliably_selected_value(field_name, cleaned)

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


def _exact_label_field(
    normalized: str, labels: dict[str, tuple[str, ...]]
) -> str | None:
    label_text = normalized.rstrip(" :-_")
    matches = [
        field_name
        for field_name, aliases in labels.items()
        if label_text in {_normalize(alias) for alias in aliases}
    ]
    return matches[0] if len(matches) == 1 else None


def _labeled_value(original: str, normalized: str, aliases: tuple[str, ...]) -> str | None:
    for alias in sorted(aliases, key=len, reverse=True):
        match = re.match(rf"^{re.escape(_normalize(alias))}\s*[:\-]\s*(.+)$", normalized)
        if not match:
            continue
        separator = re.search(r":", original) or re.search(r"\s+-\s+", original)
        if separator:
            value = original[separator.end():].strip()
            return value or None
    return None


def _bounds(polygon: Any) -> _Bounds | None:
    if not isinstance(polygon, list) or len(polygon) < 2:
        return None
    try:
        xs = [float(vertex["x"]) for vertex in polygon]
        ys = [float(vertex["y"]) for vertex in polygon]
    except (KeyError, TypeError, ValueError):
        return None
    if not all(0 <= coordinate <= 1 for coordinate in [*xs, *ys]):
        return None
    bounds = _Bounds(min(xs), min(ys), max(xs), max(ys))
    return bounds if bounds.right > bounds.left and bounds.bottom > bounds.top else None


def _is_nearby_value(label: _Bounds, value: _Bounds) -> bool:
    vertical_overlap = min(label.bottom, value.bottom) - max(label.top, value.top)
    same_row = (
        value.left >= label.right - 0.01
        and value.left - label.right <= 0.30
        and (
            vertical_overlap > 0
            or abs(value.center_y - label.center_y) <= max(0.02, label.height, value.height)
        )
    )
    vertical_gap = value.top - label.bottom
    horizontal_overlap = min(label.right, value.right) - max(label.left, value.left)
    immediately_below = (
        -0.005 <= vertical_gap <= 0.075
        and (horizontal_overlap > 0 or abs(value.left - label.left) <= 0.08)
    )
    return same_row or immediately_below


def _reliably_selected_value(field_name: str, value: str) -> str | None:
    selected = re.fullmatch(
        r"\s*(?:\[\s*[xX✓✔]\s*\]|[☒✓✔])\s*(.+?)\s*", value
    )
    if selected is None:
        return None
    selected_value = selected.group(1).strip()
    if field_name == "requestedTermMonths":
        term = re.fullmatch(r"([0-9]{1,3})(?:\s+months?)?", selected_value, re.IGNORECASE)
        return term.group(1) if term else None
    if field_name == "collateral.type":
        return selected_value.upper().replace(" ", "_")
    return None


def _confidence(value: Any) -> float | None:
    if isinstance(value, (int, float)) and 0 <= float(value) <= 1:
        return float(value)
    return None


def _combined_confidence(label: float | None, value: float | None) -> float | None:
    known = [confidence for confidence in (label, value) if confidence is not None]
    return min(known) if known else None


def _confidence_rank(value: float | None) -> float:
    return value if value is not None else -1.0
