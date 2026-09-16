package com.meridian.platform.partner.application.service;

import com.meridian.platform.partner.application.dto.ChangePartnerCompanyStatusRequest;
import com.meridian.platform.partner.application.dto.CreatePartnerCompanyRequest;
import com.meridian.platform.partner.application.dto.PartnerCompanyDto;
import com.meridian.platform.partner.application.dto.UpdatePartnerCompanyRequest;
import com.meridian.platform.partner.application.mapper.PartnerCompanyMapper;
import com.meridian.platform.partner.application.port.in.ManagePartnerCompanyUseCase;
import com.meridian.platform.partner.application.port.out.PartnerCompanyRepository;
import com.meridian.platform.partner.domain.model.PartnerCompany;
import com.meridian.platform.shared.application.audit.BusinessAuditEntry;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayload;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayloadKey;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class ManagePartnerCompanyService implements ManagePartnerCompanyUseCase {

    private final PartnerCompanyRepository companies;
    private final PartnerCompanyMapper mapper;
    private final CurrentUserProvider currentUserProvider;
    private final BusinessAuditPublisher auditPublisher;
    private final Clock clock;

    public ManagePartnerCompanyService(
            PartnerCompanyRepository companies,
            PartnerCompanyMapper mapper,
            CurrentUserProvider currentUserProvider,
            BusinessAuditPublisher auditPublisher,
            Clock clock
    ) {
        this.companies = companies;
        this.mapper = mapper;
        this.currentUserProvider = currentUserProvider;
        this.auditPublisher = auditPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PartnerCompanyDto create(CreatePartnerCompanyRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String companyCode = request.companyCode().trim();
        if (companies.existsByCompanyCode(companyCode)) {
            throw companyCodeConflict();
        }
        PartnerCompany saved = companies.save(new PartnerCompany(
                UUID.randomUUID(), companyCode, request.name(), request.status(),
                request.salaryAdvancePolicyLimit()
        ));
        publish(saved, BusinessAuditAction.PARTNER_COMPANY_CREATED);
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    public PartnerCompanyDto update(UUID partnerCompanyId, UpdatePartnerCompanyRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        PartnerCompany current = companyForUpdate(partnerCompanyId);
        PartnerCompany updated = current.update(request.name(), request.salaryAdvancePolicyLimit());
        if (updated.equals(current)) {
            return mapper.toDto(current);
        }
        PartnerCompany saved = companies.save(updated);
        publish(saved, BusinessAuditAction.PARTNER_COMPANY_UPDATED);
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    public PartnerCompanyDto changeStatus(
            UUID partnerCompanyId,
            ChangePartnerCompanyStatusRequest request
    ) {
        Objects.requireNonNull(request, "request must not be null");
        PartnerCompany current = companyForUpdate(partnerCompanyId);
        if (current.status() == request.status()) {
            return mapper.toDto(current);
        }
        PartnerCompany saved = companies.save(current.changeStatus(request.status()));
        publish(saved, BusinessAuditAction.PARTNER_COMPANY_STATUS_CHANGED);
        return mapper.toDto(saved);
    }

    private PartnerCompany companyForUpdate(UUID partnerCompanyId) {
        Objects.requireNonNull(partnerCompanyId, "partnerCompanyId must not be null");
        return companies.findByIdForUpdate(partnerCompanyId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "PARTNER_COMPANY_NOT_FOUND", "Partner company was not found."
                ));
    }

    private void publish(PartnerCompany company, BusinessAuditAction action) {
        AuthenticatedUser actor = currentUserProvider.currentUser();
        LocalDateTime occurredAt = LocalDateTime.now(clock);
        BusinessAuditPayload payload = BusinessAuditPayload.builder()
                .put(BusinessAuditPayloadKey.PARTNER_COMPANY_ID, company.id())
                .put(BusinessAuditPayloadKey.PARTNER_COMPANY_STATUS, company.status())
                .build();
        auditPublisher.publish(BusinessAuditEvent.single(
                BusinessOperationContext.user(UUID.randomUUID(), actor.userId(), occurredAt),
                new BusinessAuditEntry(action, BusinessAuditEntityType.PARTNER_COMPANY, company.id(), payload)
        ));
    }

    public static BusinessStateConflictException companyCodeConflict() {
        return new BusinessStateConflictException(
                "PARTNER_COMPANY_CODE_ALREADY_EXISTS",
                "Partner company code is already in use."
        );
    }
}
