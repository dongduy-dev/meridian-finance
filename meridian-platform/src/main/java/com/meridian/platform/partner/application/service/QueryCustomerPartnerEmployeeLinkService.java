package com.meridian.platform.partner.application.service;

import com.meridian.platform.partner.application.dto.CustomerPartnerEmployeeLinkSnapshotDto;
import com.meridian.platform.partner.application.port.in.QueryCustomerPartnerEmployeeLinkUseCase;
import com.meridian.platform.partner.application.port.out.CustomerPartnerEmployeeLinkRepository;
import com.meridian.platform.partner.application.port.out.PartnerCompanyRepository;
import com.meridian.platform.partner.application.port.out.PartnerEmployeeRepository;
import com.meridian.platform.partner.domain.model.CustomerPartnerEmployeeLink;
import com.meridian.platform.partner.domain.model.PartnerCompanyStatus;
import com.meridian.platform.partner.domain.model.PartnerEmployee;
import com.meridian.platform.partner.domain.model.PartnerEmployeeStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class QueryCustomerPartnerEmployeeLinkService implements QueryCustomerPartnerEmployeeLinkUseCase {

    private final CustomerPartnerEmployeeLinkRepository customerPartnerEmployeeLinkRepository;
    private final PartnerEmployeeRepository partnerEmployeeRepository;
    private final PartnerCompanyRepository partnerCompanyRepository;

    public QueryCustomerPartnerEmployeeLinkService(
            CustomerPartnerEmployeeLinkRepository customerPartnerEmployeeLinkRepository,
            PartnerEmployeeRepository partnerEmployeeRepository,
            PartnerCompanyRepository partnerCompanyRepository
    ) {
        this.customerPartnerEmployeeLinkRepository = customerPartnerEmployeeLinkRepository;
        this.partnerEmployeeRepository = partnerEmployeeRepository;
        this.partnerCompanyRepository = partnerCompanyRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerPartnerEmployeeLinkSnapshotDto> findVerifiedActiveLink(
            UUID customerId,
            UUID customerPartnerEmployeeLinkId
    ) {
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(customerPartnerEmployeeLinkId, "customerPartnerEmployeeLinkId must not be null");

        return customerPartnerEmployeeLinkRepository.findById(customerPartnerEmployeeLinkId)
                .filter(link -> link.customerId().equals(customerId))
                .filter(CustomerPartnerEmployeeLink::isVerified)
                .flatMap(this::toVerifiedActiveSnapshot);
    }

    private Optional<CustomerPartnerEmployeeLinkSnapshotDto> toVerifiedActiveSnapshot(
            CustomerPartnerEmployeeLink link
    ) {
        Optional<PartnerEmployee> activeEmployee = partnerEmployeeRepository.findById(link.partnerEmployeeId())
                .filter(employee -> employee.active() && employee.employmentStatus() == PartnerEmployeeStatus.ACTIVE);

        if (activeEmployee.isEmpty()) {
            return Optional.empty();
        }

        return partnerCompanyRepository.findById(link.partnerCompanyId())
                .filter(company -> company.status() == PartnerCompanyStatus.ACTIVE)
                .map(company -> new CustomerPartnerEmployeeLinkSnapshotDto(
                        link.customerId(),
                        link.id(),
                        link.partnerCompanyId(),
                        link.partnerEmployeeId(),
                        link.sourceImportBatchId(),
                        activeEmployee.get().salaryAdvanceLimit(),
                        link.lastVerifiedAt(),
                        link.lastRefreshedAt()
                ));
    }
}
