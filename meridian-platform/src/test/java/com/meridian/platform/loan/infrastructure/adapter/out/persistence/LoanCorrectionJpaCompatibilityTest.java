package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.*;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LoanCorrectionJpaCompatibilityTest {
    private final UUID id = UUID.randomUUID();
    private final LocalDateTime time = LocalDateTime.of(2026, 10, 27, 12, 0);

    @Test
    void everyHistoricalReasonStringRoundTripsThroughTheUnchangedRequestColumn() throws Exception {
        for (String oldCode : new String[]{"SUPPORTING_DOCUMENT_REQUIRED", "RECENT_PAYSLIP_REQUIRED", "DOCUMENT_REPLACEMENT_REQUIRED", "DOCUMENT_REVIEW_REQUIRED"}) {
            var source = new LoanCorrectionRequest(id, id, id, "REQUEST_REPLACEMENT", LoanCorrectionReasonCode.valueOf(oldCode), id,
                    LoanCorrectionRequestStatus.OPEN, null, time, null, null);
            var entity = new LoanCorrectionRequestJpaEntity(source);
            assertEquals(source, entity.toDomain());
            assertEquals(oldCode, entity.toDomain().reasonCode().name());
            entity.updateFrom(source);
            assertEquals(source, entity.toDomain());
        }
        assertStringColumn(LoanCorrectionRequestJpaEntity.class, "reasonCode", "reason_code");
    }

    @Test
    void everyHistoricalDocumentStringAndExecutableScopeKeepTaskSequenceAndVersionIdentity() throws Exception {
        for (String oldCode : new String[]{"RECENT_PAYSLIP", "INCOME_PROOF", "BANK_STATEMENT", "EMPLOYMENT_PROOF", "COLLATERAL_OWNERSHIP_EVIDENCE"}) {
            for (var scope : LoanCorrectionScope.values()) {
                boolean review = scope == LoanCorrectionScope.DOCUMENT_REVIEW;
                boolean upload = scope == LoanCorrectionScope.SUPPORTING_DOCUMENT_UPLOAD;
                var task = new LoanCorrectionTask(id, id, 7,
                        review ? LoanCorrectionResponsibility.STAFF : LoanCorrectionResponsibility.CUSTOMER, scope,
                        LoanDocumentType.valueOf(oldCode), upload, id, upload ? null : id,
                        review ? null : " instruction ", review ? " instruction " : null,
                        LoanCorrectionTaskStatus.OPEN, null, null, null, time);
                var entity = new LoanCorrectionTaskJpaEntity(task);
                assertEquals(task, entity.toDomain());
                entity.updateFrom(task);
                assertEquals(task, entity.toDomain());
                assertEquals(oldCode, entity.toDomain().documentType().name());
            }
        }
        assertStringColumn(LoanCorrectionTaskJpaEntity.class, "documentType", "document_type");
        assertStringColumn(LoanCorrectionTaskJpaEntity.class, "scope", "scope");
        assertStringColumn(LoanCorrectionTaskJpaEntity.class, "responsibleParty", "responsible_party");
        assertFalse(Arrays.stream(LoanCorrectionScope.values()).anyMatch(scope -> scope.name().equals("APPLICATION_TERMS")));
    }

    private void assertStringColumn(Class<?> entity, String fieldName, String columnName) throws Exception {
        var field = entity.getDeclaredField(fieldName);
        assertEquals(EnumType.STRING, field.getAnnotation(Enumerated.class).value());
        assertEquals(columnName, field.getAnnotation(Column.class).name());
    }
}
