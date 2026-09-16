package com.meridian.platform.partner.application.service;

import com.meridian.platform.partner.application.dto.ChangePartnerCompanyStatusRequest;
import com.meridian.platform.partner.application.dto.CreatePartnerCompanyRequest;
import com.meridian.platform.partner.application.dto.UpdatePartnerCompanyRequest;
import com.meridian.platform.partner.application.mapper.PartnerCompanyMapper;
import com.meridian.platform.partner.application.port.out.PartnerCompanyRepository;
import com.meridian.platform.partner.domain.model.PartnerCompany;
import com.meridian.platform.partner.domain.model.PartnerCompanyStatus;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManagePartnerCompanyServiceTest {

    private final PartnerCompanyRepository companies = mock(PartnerCompanyRepository.class);
    private final CurrentUserProvider users = mock(CurrentUserProvider.class);
    private final BusinessAuditPublisher audits = mock(BusinessAuditPublisher.class);
    private final UUID companyId = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private ManagePartnerCompanyService service;

    @BeforeEach
    void setUp() {
        when(users.currentUser()).thenReturn(new AuthenticatedUser(
                UUID.randomUUID(), "admin@meridian.local", "STAFF", null,
                Set.of("BACK_OFFICE_ADMIN"), Set.of("partner:manage")
        ));
        when(companies.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new ManagePartnerCompanyService(
                companies, new PartnerCompanyMapper(), users, audits,
                Clock.fixed(Instant.parse("2026-09-16T08:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void createsValidatedCompanyAndAuditsIt() {
        var result = service.create(new CreatePartnerCompanyRequest(
                " ACME ", " Acme Ltd ", PartnerCompanyStatus.ACTIVE, new BigDecimal("20000000.00")
        ));
        assertEquals("ACME", result.companyCode());
        assertEquals("Acme Ltd", result.name());
        var audit = ArgumentCaptor.forClass(com.meridian.platform.shared.application.audit.BusinessAuditEvent.class);
        verify(audits).publish(audit.capture());
        assertEquals(BusinessAuditAction.PARTNER_COMPANY_CREATED, audit.getValue().entries().getFirst().action());
    }

    @Test
    void rejectsDuplicateCompanyCode() {
        when(companies.existsByCompanyCode("ACME")).thenReturn(true);
        BusinessStateConflictException error = assertThrows(BusinessStateConflictException.class, () ->
                service.create(new CreatePartnerCompanyRequest(
                        "ACME", "Acme", PartnerCompanyStatus.ACTIVE, BigDecimal.ZERO
                )));
        assertEquals("PARTNER_COMPANY_CODE_ALREADY_EXISTS", error.getErrorCode());
        verify(companies, never()).save(any());
    }

    @Test
    void updatesOnlyMutableFactsUnderLock() {
        when(companies.findByIdForUpdate(companyId)).thenReturn(Optional.of(company(PartnerCompanyStatus.ACTIVE)));
        var result = service.update(companyId, new UpdatePartnerCompanyRequest("Updated", new BigDecimal("9000000")));
        assertEquals("ACME", result.companyCode());
        assertEquals("Updated", result.name());
        assertEquals(new BigDecimal("9000000"), result.salaryAdvancePolicyLimit());
    }

    @Test
    void sameStatusIsNoOpWithoutAuditOrSave() {
        when(companies.findByIdForUpdate(companyId)).thenReturn(Optional.of(company(PartnerCompanyStatus.SUSPENDED)));
        service.changeStatus(companyId, new ChangePartnerCompanyStatusRequest(PartnerCompanyStatus.SUSPENDED));
        verify(companies, never()).save(any());
        verify(audits, never()).publish(any());
    }

    @Test
    void changesStatusUnderLockAndAuditsTheTransition() {
        when(companies.findByIdForUpdate(companyId)).thenReturn(Optional.of(company(PartnerCompanyStatus.ACTIVE)));

        var result = service.changeStatus(
                companyId,
                new ChangePartnerCompanyStatusRequest(PartnerCompanyStatus.SUSPENDED)
        );

        assertEquals("SUSPENDED", result.status());
        verify(companies).findByIdForUpdate(companyId);
        verify(companies).save(any());
        var audit = ArgumentCaptor.forClass(com.meridian.platform.shared.application.audit.BusinessAuditEvent.class);
        verify(audits).publish(audit.capture());
        assertEquals(BusinessAuditAction.PARTNER_COMPANY_STATUS_CHANGED,
                audit.getValue().entries().getFirst().action());
    }

    @Test
    void rejectsNegativePolicyLimitBeforePersistence() {
        assertThrows(IllegalArgumentException.class, () -> service.create(new CreatePartnerCompanyRequest(
                "ACME", "Acme", PartnerCompanyStatus.ACTIVE, new BigDecimal("-0.01")
        )));
        verify(companies, never()).save(any());
    }

    private PartnerCompany company(PartnerCompanyStatus status) {
        return new PartnerCompany(companyId, "ACME", "Acme", status, new BigDecimal("20000000"));
    }
}
