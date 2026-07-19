package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StartLoanApplicationReviewServiceTest {

    private final UUID applicationId = UUID.randomUUID();
    private LoanApplicationRepository applicationRepository;
    private LoanDocumentChecklistPort documentChecklistPort;
    private LoanApplicationStatusTransitionRecorder transitionRecorder;
    private BusinessAuditPublisher auditPublisher;
    private StartLoanApplicationReviewService service;

    @BeforeEach
    void setUp() {
        applicationRepository = mock(LoanApplicationRepository.class);
        documentChecklistPort = mock(LoanDocumentChecklistPort.class);
        transitionRecorder = mock(LoanApplicationStatusTransitionRecorder.class);
        auditPublisher = mock(BusinessAuditPublisher.class);
        CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
        when(currentUserProvider.currentUser()).thenReturn(new AuthenticatedUser(
                UUID.randomUUID(),
                "loan.officer@meridian.local",
                "STAFF",
                null,
                Set.of("LOAN_OFFICER"),
                Set.of("loan:review")
        ));
        when(applicationRepository.findByIdForUpdate(applicationId)).thenReturn(Optional.of(application()));
        service = new StartLoanApplicationReviewService(
                applicationRepository,
                documentChecklistPort,
                transitionRecorder,
                auditPublisher,
                currentUserProvider,
                Clock.fixed(Instant.parse("2026-07-19T08:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void rejectsReviewStartWhenChecklistIsNotProcessingReady() {
        when(documentChecklistPort.isProcessingReady(applicationId)).thenReturn(false);

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> service.startReview(applicationId)
        );

        assertEquals("LOAN_REVIEW_DOCUMENTS_NOT_READY", exception.getErrorCode());
        verify(applicationRepository).acquireWorkflowLock(applicationId);
        verify(applicationRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void startsReviewExplicitlyWhenChecklistIsProcessingReady() {
        when(documentChecklistPort.isProcessingReady(applicationId)).thenReturn(true);
        when(applicationRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals("UNDER_REVIEW", service.startReview(applicationId).status());
        verify(transitionRecorder).record(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.isNull()
        );
    }

    private LoanApplication application() {
        return new LoanApplication(
                applicationId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "SA-20260719-000001",
                ProductCode.SALARY_ADVANCE,
                ProductType.SALARY_BASED,
                LoanApplicationStatus.SUBMITTED,
                BigDecimal.valueOf(3_000_000).setScale(2),
                1,
                LocalDateTime.of(2026, 7, 19, 7, 0)
        );
    }
}
