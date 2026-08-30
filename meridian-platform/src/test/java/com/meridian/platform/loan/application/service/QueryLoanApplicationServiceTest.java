package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.LoanApplicationStatusDto;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanCorrectionRepository;
import com.meridian.platform.loan.application.port.out.ApprovedOfferRepository;
import com.meridian.platform.loan.application.port.out.LoanContractRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.ApprovedOffer;
import com.meridian.platform.loan.domain.model.ApprovedOfferStatus;
import com.meridian.platform.loan.domain.model.LoanContract;
import com.meridian.platform.loan.domain.model.LoanContractStatus;
import com.meridian.platform.loan.domain.model.LoanCorrectionRequest;
import com.meridian.platform.loan.domain.model.LoanCorrectionRequestStatus;
import com.meridian.platform.loan.domain.model.LoanCorrectionTask;
import com.meridian.platform.loan.domain.model.LoanCorrectionTaskStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class QueryLoanApplicationServiceTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID APPLICATION_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    @Mock LoanApplicationRepository applications;
    @Mock CurrentUserProvider currentUserProvider;
    @Mock LoanCorrectionRepository corrections;
    @Mock ApprovedOfferRepository offers;
    @Mock LoanContractRepository contracts;

    private QueryLoanApplicationService service;

    @BeforeEach
    void setUp() {
        service = new QueryLoanApplicationService(
                applications,
                currentUserProvider,
                corrections,
                offers,
                contracts,
                Clock.fixed(java.time.Instant.parse("2026-08-10T08:00:00Z"), ZoneOffset.UTC)
        );
        lenient().when(applications.findById(APPLICATION_ID)).thenReturn(Optional.of(application(CUSTOMER_ID)));
    }

    @Test
    void customerReadsOwnMinimalStatusProjection() {
        when(currentUserProvider.currentUser()).thenReturn(customer(CUSTOMER_ID));

        LoanApplicationStatusDto result = service.query(APPLICATION_ID);

        assertEquals(APPLICATION_ID, result.loanApplicationId());
        assertEquals("SALARY_ADVANCE", result.productCode());
        assertEquals("UNDER_REVIEW", result.status());
        verify(applications, never()).save(any());
        verify(applications, never()).acquireWorkflowLock(any());
    }

    @Test
    void foreignCustomerAndMissingApplicationUseTheSameConcealedNotFoundCode() {
        when(currentUserProvider.currentUser()).thenReturn(customer(UUID.randomUUID()));
        EntityNotFoundException foreign = assertThrows(
                EntityNotFoundException.class,
                () -> service.query(APPLICATION_ID)
        );

        when(applications.findById(APPLICATION_ID)).thenReturn(Optional.empty());
        EntityNotFoundException missing = assertThrows(
                EntityNotFoundException.class,
                () -> service.query(APPLICATION_ID)
        );

        assertEquals("LOAN_APPLICATION_NOT_FOUND", foreign.getErrorCode());
        assertEquals(foreign.getErrorCode(), missing.getErrorCode());
    }

    @Test
    void staffWithLoanReadCanReadApplication() {
        when(currentUserProvider.currentUser()).thenReturn(staff(Set.of("loan:read")));

        assertEquals("UNDER_REVIEW", service.query(APPLICATION_ID).status());
    }

    @Test
    void unrelatedPermissionsDoNotGrantReadAccess() {
        when(currentUserProvider.currentUser()).thenReturn(staff(Set.of("approval:decide")));

        AuthorizationException exception = assertThrows(
                AuthorizationException.class,
                () -> service.query(APPLICATION_ID)
        );

        assertEquals("LOAN_APPLICATION_ACCESS_DENIED", exception.getErrorCode());
    }

    @Test
    void customerIndexUsesOwnedRepositoryOrderAndProjectsDocumentAndTerminalStates() {
        when(currentUserProvider.currentUser()).thenReturn(customer(CUSTOMER_ID));
        LoanApplication documentsPending = application(
                UUID.randomUUID(), CUSTOMER_ID, LoanApplicationStatus.DOCUMENTS_PENDING,
                LocalDateTime.of(2026, 8, 11, 8, 0));
        LoanApplication rejected = application(
                UUID.randomUUID(), CUSTOMER_ID, LoanApplicationStatus.REJECTED,
                LocalDateTime.of(2026, 8, 10, 8, 0));
        when(applications.findByCustomerIdOrderBySubmittedAtDesc(CUSTOMER_ID))
                .thenReturn(List.of(documentsPending, rejected));

        var result = service.queryOwnApplications();

        assertEquals(documentsPending.id(), result.getFirst().loanApplicationId());
        assertEquals("UPLOAD_DOCUMENTS", result.getFirst().requiredAction().name());
        assertEquals(true, result.getFirst().lifecycleActive());
        assertEquals("NONE", result.getLast().requiredAction().name());
        assertEquals(false, result.getLast().lifecycleActive());
    }

    @Test
    void actionProjectionRequiresOwningOfferCorrectionOrContractEvidence() {
        when(currentUserProvider.currentUser()).thenReturn(customer(CUSTOMER_ID));
        LoanApplication correction = application(
                UUID.randomUUID(), CUSTOMER_ID, LoanApplicationStatus.RETURNED_FOR_REVISION,
                LocalDateTime.of(2026, 8, 12, 8, 0));
        LoanApplication offerApplication = application(
                UUID.randomUUID(), CUSTOMER_ID, LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING,
                LocalDateTime.of(2026, 8, 11, 8, 0));
        LoanApplication contractApplication = application(
                UUID.randomUUID(), CUSTOMER_ID, LoanApplicationStatus.CONTRACT_PENDING,
                LocalDateTime.of(2026, 8, 10, 8, 0));
        when(applications.findByCustomerIdOrderBySubmittedAtDesc(CUSTOMER_ID))
                .thenReturn(List.of(correction, offerApplication, contractApplication));
        LoanCorrectionRequest request = mock(LoanCorrectionRequest.class);
        LoanCorrectionTask task = mock(LoanCorrectionTask.class);
        when(request.isActive()).thenReturn(true);
        when(request.status()).thenReturn(LoanCorrectionRequestStatus.OPEN);
        when(task.status()).thenReturn(LoanCorrectionTaskStatus.OPEN);
        when(corrections.findLatestRequestByApplicationId(correction.id()))
                .thenReturn(Optional.of(request));
        when(corrections.findCustomerTasks(correction.id(), CUSTOMER_ID)).thenReturn(List.of(task));
        ApprovedOffer offer = mock(ApprovedOffer.class);
        when(offer.effectiveStatusAt(any())).thenReturn(ApprovedOfferStatus.PENDING);
        when(offers.findByLoanApplicationId(offerApplication.id())).thenReturn(Optional.of(offer));
        LoanContract contract = mock(LoanContract.class);
        when(contract.status()).thenReturn(LoanContractStatus.PREPARED);
        when(contracts.findCurrentByApplicationId(contractApplication.id()))
                .thenReturn(Optional.of(contract));

        var result = service.queryOwnApplications();

        assertEquals("COMPLETE_CORRECTIONS", result.get(0).requiredAction().name());
        assertEquals("REVIEW_APPROVED_OFFER", result.get(1).requiredAction().name());
        assertEquals("ACKNOWLEDGE_CONTRACT", result.get(2).requiredAction().name());
    }

    @Test
    void staffCannotUseCustomerApplicationIndex() {
        when(currentUserProvider.currentUser()).thenReturn(staff(Set.of("loan:read")));

        assertThrows(AuthorizationException.class, service::queryOwnApplications);
        verify(applications, never()).findByCustomerIdOrderBySubmittedAtDesc(any());
    }

    private static LoanApplication application(UUID customerId) {
        return application(APPLICATION_ID, customerId, LoanApplicationStatus.UNDER_REVIEW,
                LocalDateTime.of(2026, 8, 10, 8, 0));
    }

    private static LoanApplication application(
            UUID applicationId,
            UUID customerId,
            LoanApplicationStatus status,
            LocalDateTime submittedAt
    ) {
        return new LoanApplication(
                applicationId,
                customerId,
                UUID.randomUUID(),
                "SA-20260810-000001",
                ProductCode.SALARY_ADVANCE,
                ProductType.SALARY_BASED,
                status,
                BigDecimal.valueOf(3_000_000).setScale(2),
                1,
                submittedAt
        );
    }

    private static AuthenticatedUser customer(UUID customerId) {
        return new AuthenticatedUser(
                UUID.randomUUID(), "customer@meridian.test", "CUSTOMER", customerId,
                Set.of("CUSTOMER"), Set.of("loan:read:own")
        );
    }

    private static AuthenticatedUser staff(Set<String> permissions) {
        return new AuthenticatedUser(
                UUID.randomUUID(), "staff@meridian.test", "STAFF", null,
                Set.of("LOAN_OFFICER"), permissions
        );
    }
}
