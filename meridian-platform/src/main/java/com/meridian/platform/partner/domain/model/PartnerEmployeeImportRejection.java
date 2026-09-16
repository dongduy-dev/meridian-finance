package com.meridian.platform.partner.domain.model;

public record PartnerEmployeeImportRejection(
        int rowIndex,
        String errorCode,
        String reason
) {
}
