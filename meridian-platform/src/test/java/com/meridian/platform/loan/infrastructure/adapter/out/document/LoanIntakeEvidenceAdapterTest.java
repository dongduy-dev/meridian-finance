package com.meridian.platform.loan.infrastructure.adapter.out.document;

import com.meridian.platform.document.application.port.in.LoanIntakeEvidenceUseCase;
import com.meridian.platform.document.application.port.out.IntakeDocumentRepository;
import com.meridian.platform.document.application.service.LoanIntakeEvidenceService;
import com.meridian.platform.document.domain.model.IntakeDocument;
import com.meridian.platform.document.domain.model.IntakeEvidenceType;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoanIntakeEvidenceAdapterTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void preservesCaseIdentityAndLockedCurrentPaperLookup(boolean collateral) {
        var repository = mock(IntakeDocumentRepository.class);
        var adapter = new LoanIntakeEvidenceAdapter(new LoanIntakeEvidenceService(repository));
        UUID caseId = UUID.randomUUID();
        var type = type(collateral);
        when(repository.findByCaseAndTypeForUpdate(caseId, type)).thenReturn(Optional.of(
                new IntakeDocument(UUID.randomUUID(), caseId, type, UUID.randomUUID(), LocalDateTime.MIN, LocalDateTime.MIN)));

        require(adapter, collateral, caseId);

        verify(repository).findByCaseAndTypeForUpdate(caseId, type);
        verifyNoMoreInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingWrongTypeAndAbsentCurrentVersionKeepTheirErrorContract(boolean collateral) {
        UUID caseId = UUID.randomUUID();
        for (int scenario = 0; scenario < 3; scenario++) {
            var repository = mock(IntakeDocumentRepository.class);
            var adapter = new LoanIntakeEvidenceAdapter(new LoanIntakeEvidenceService(repository));
            var requested = type(collateral);
            var storedType = scenario == 1 ? type(!collateral) : requested;
            var stored = new IntakeDocument(UUID.randomUUID(), caseId, storedType,
                    scenario == 2 ? null : UUID.randomUUID(), LocalDateTime.MIN, LocalDateTime.MIN);
            int currentScenario = scenario;
            when(repository.findByCaseAndTypeForUpdate(caseId, requested)).thenAnswer(call ->
                    currentScenario == 0 || call.getArgument(1) != stored.evidenceType()
                            ? Optional.empty() : Optional.of(stored));

            var failure = assertThrows(BusinessRuleViolationException.class, () -> require(adapter, collateral, caseId));

            assertEquals(collateral ? "COLLATERAL_PAPER_APPLICATION_REQUIRED" : "UCL_PAPER_APPLICATION_REQUIRED", failure.getErrorCode());
            assertEquals(collateral ? "A current signed Collateral paper application is required before conversion."
                    : "A current signed UCL paper application is required before conversion.", failure.getMessage());
            verify(repository).findByCaseAndTypeForUpdate(caseId, requested);
            verifyNoMoreInteractions(repository);
        }
    }

    @Test
    void propagatesProviderFailuresWithoutTranslation() {
        var failure = new IllegalStateException("locked evidence lookup failed");
        var provider = mock(LoanIntakeEvidenceUseCase.class, call -> { throw failure; });
        var adapter = new LoanIntakeEvidenceAdapter(provider);
        UUID caseId = UUID.randomUUID();
        assertSame(failure, assertThrows(IllegalStateException.class, () -> adapter.requireCurrentUclPaperApplication(caseId)));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> adapter.requireCurrentCollateralPaperApplication(caseId)));
        verify(provider).requireCurrentUclPaperApplication(caseId);
        verify(provider).requireCurrentCollateralPaperApplication(caseId);
    }

    private static IntakeEvidenceType type(boolean collateral) {
        return collateral ? IntakeEvidenceType.COLLATERAL_PAPER_APPLICATION : IntakeEvidenceType.UCL_PAPER_APPLICATION;
    }

    private static void require(LoanIntakeEvidenceAdapter adapter, boolean collateral, UUID caseId) {
        if (collateral) adapter.requireCurrentCollateralPaperApplication(caseId);
        else adapter.requireCurrentUclPaperApplication(caseId);
    }
}
