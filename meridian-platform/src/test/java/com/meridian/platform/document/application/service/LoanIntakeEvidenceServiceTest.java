package com.meridian.platform.document.application.service;

import com.meridian.platform.document.application.port.out.IntakeDocumentRepository;
import com.meridian.platform.document.domain.model.IntakeDocument;
import com.meridian.platform.document.domain.model.IntakeEvidenceType;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanIntakeEvidenceServiceTest {

    private static final UUID CASE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock IntakeDocumentRepository documents;

    @Test
    void requiresTheCurrentCollateralPaperApplicationSpecifically() {
        LoanIntakeEvidenceService service = new LoanIntakeEvidenceService(documents);
        when(documents.findByCaseAndTypeForUpdate(
                CASE_ID, IntakeEvidenceType.COLLATERAL_PAPER_APPLICATION))
                .thenReturn(Optional.empty());

        BusinessRuleViolationException failure = assertThrows(
                BusinessRuleViolationException.class,
                () -> service.requireCurrentCollateralPaperApplication(CASE_ID));

        assertEquals("COLLATERAL_PAPER_APPLICATION_REQUIRED", failure.getErrorCode());
        verify(documents).findByCaseAndTypeForUpdate(
                CASE_ID, IntakeEvidenceType.COLLATERAL_PAPER_APPLICATION);
    }

    @Test
    void acceptsOnlyARecordWithACurrentVersion() {
        LoanIntakeEvidenceService service = new LoanIntakeEvidenceService(documents);
        LocalDateTime now = LocalDateTime.of(2026, 9, 18, 8, 0);
        when(documents.findByCaseAndTypeForUpdate(
                CASE_ID, IntakeEvidenceType.COLLATERAL_PAPER_APPLICATION))
                .thenReturn(Optional.of(new IntakeDocument(
                        UUID.randomUUID(), CASE_ID, IntakeEvidenceType.COLLATERAL_PAPER_APPLICATION,
                        UUID.randomUUID(), now, now)));

        service.requireCurrentCollateralPaperApplication(CASE_ID);
    }
}
