package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.RecordedCustomerActionDto;
import com.meridian.platform.loan.application.dto.StaffLoanApplicationCaseDto;
import com.meridian.platform.loan.application.mapper.ApprovedOfferMapper;
import com.meridian.platform.loan.application.port.out.*;
import com.meridian.platform.loan.domain.model.*;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QueryAssistedApprovedOfferResponseServiceTest {
    private final UUID appId = UUID.randomUUID(), customerId = UUID.randomUUID(), offerId = UUID.randomUUID();
    private final LoanApplicationRepository applications = mock(LoanApplicationRepository.class);
    private final ApprovedOfferRepository offers = mock(ApprovedOfferRepository.class);
    private final LoanAssistedActionEvidencePort evidence = mock(LoanAssistedActionEvidencePort.class);
    private final CurrentUserProvider users = mock(CurrentUserProvider.class);
    private final ApprovedOfferMapper mapper = mock(ApprovedOfferMapper.class);
    private final AssistedCustomerActionProvenanceComposer provenance = mock(AssistedCustomerActionProvenanceComposer.class);
    private final QueryAssistedApprovedOfferResponseService service = new QueryAssistedApprovedOfferResponseService(
            applications, offers, evidence, users, mapper,
            Clock.fixed(Instant.parse("2026-10-01T08:00:00Z"), ZoneOffset.UTC), provenance);

    @Test void completedReadReturnsValidatedProvenanceAndNeverReplacesConsumedEvidenceWithAnotherLookup() {
        var application = prepare(LoanApplicationStatus.CONTRACT_PENDING);
        var offer = mock(ApprovedOffer.class);
        when(offer.status()).thenReturn(ApprovedOfferStatus.ACCEPTED);
        when(offer.effectiveStatusAt(any())).thenReturn(ApprovedOfferStatus.ACCEPTED);
        when(offers.findByLoanApplicationId(appId)).thenReturn(Optional.of(offer));
        var recorded = new RecordedCustomerActionDto("ACCEPT", new StaffLoanApplicationCaseDto.StaffActorDto(
                UUID.fromString("00000000-0000-0000-0000-000000000302"), "Deni Loan Officer", "deni@meridian.local"),
                LocalDateTime.of(2026, 10, 1, 8, 0), null);
        when(provenance.offer(application, offer)).thenReturn(recorded);
        var result = service.query(appId);
        assertSame(recorded, result.completedResponse());
        String json = tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build().writeValueAsString(result);
        assertFalse(json.contains(customerId.toString()));
        assertFalse(json.contains("customer@meridian.local"));
        verifyNoInteractions(evidence);
        verify(applications, never()).save(any());
        verify(offers, never()).save(any());
    }

    @Test void pendingReadHasNoInventedCompletedRecord() {
        prepare(LoanApplicationStatus.CUSTOMER_ACCEPTANCE_PENDING);
        var offer = mock(ApprovedOffer.class);
        when(offer.id()).thenReturn(offerId);
        when(offer.status()).thenReturn(ApprovedOfferStatus.PENDING);
        when(offers.findByLoanApplicationId(appId)).thenReturn(Optional.of(offer));
        var result = service.query(appId);
        assertNull(result.completedResponse());
        assertEquals("ACTION_AVAILABLE", result.workState());
        verify(evidence).findOfferEvidence(appId, offerId);
    }

    @Test void contradictoryCompletedEvidencePropagatesSystemConflict() {
        var application = prepare(LoanApplicationStatus.CONTRACT_PENDING);
        var offer = mock(ApprovedOffer.class);
        when(offers.findByLoanApplicationId(appId)).thenReturn(Optional.of(offer));
        when(provenance.offer(application, offer)).thenThrow(new BusinessStateConflictException(
                "SYSTEM_STATE_CONFLICT", "Contradictory evidence."));
        assertEquals("SYSTEM_STATE_CONFLICT", assertThrows(BusinessStateConflictException.class,
                () -> service.query(appId)).getErrorCode());
    }

    @Test void readAuthorityRemainsExactPermissionAndLoanOfficerRole() {
        for (var actor : java.util.List.of(
                staff(Set.of("loan:read"), Set.of("LOAN_OFFICER")),
                staff(Set.of("loan:offer:respond:staff"), Set.of("ACCOUNTING_OFFICER")))) {
            when(users.currentUser()).thenReturn(actor);
            assertThrows(AuthorizationException.class, () -> service.query(appId));
        }
        verifyNoInteractions(applications, offers, evidence, provenance);
    }

    private LoanApplication prepare(LoanApplicationStatus status) {
        var application = new LoanApplication(appId, customerId, UUID.randomUUID(), "UCL-1",
                ProductCode.UNSECURED_CONSUMER_LOAN, ProductType.UNSECURED, OriginationChannel.STAFF_ASSISTED,
                status, BigDecimal.valueOf(5_000_000), 6, LocalDateTime.of(2026, 9, 20, 8, 0));
        when(applications.findById(appId)).thenReturn(Optional.of(application));
        when(users.currentUser()).thenReturn(staff(Set.of("loan:offer:respond:staff"), Set.of("LOAN_OFFICER")));
        return application;
    }

    private AuthenticatedUser staff(Set<String> permissions, Set<String> roles) {
        return new AuthenticatedUser(UUID.randomUUID(), "staff@meridian.local", "STAFF", null, roles, permissions);
    }
}
