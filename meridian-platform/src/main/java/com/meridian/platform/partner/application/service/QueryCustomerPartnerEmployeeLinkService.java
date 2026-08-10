package com.meridian.platform.partner.application.service;

import com.meridian.platform.partner.application.dto.CustomerPartnerEmployeeEligibilityDto;
import com.meridian.platform.partner.application.dto.CustomerPartnerEmployeeLinkSnapshotDto;
import com.meridian.platform.partner.application.port.in.QueryCustomerPartnerEmployeeLinkUseCase;
import com.meridian.platform.partner.application.port.out.CustomerPartnerEmployeeLinkRepository;
import com.meridian.platform.partner.application.port.out.PartnerCompanyRepository;
import com.meridian.platform.partner.application.port.out.PartnerEmployeeImportBatchRepository;
import com.meridian.platform.partner.application.port.out.PartnerEmployeeRepository;
import com.meridian.platform.partner.domain.model.CustomerPartnerEmployeeLink;
import com.meridian.platform.partner.domain.model.PartnerCompany;
import com.meridian.platform.partner.domain.model.PartnerCompanyStatus;
import com.meridian.platform.partner.domain.model.PartnerEmployee;
import com.meridian.platform.partner.domain.model.PartnerEmployeeImportBatch;
import com.meridian.platform.partner.domain.model.PartnerEmployeeStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class QueryCustomerPartnerEmployeeLinkService implements QueryCustomerPartnerEmployeeLinkUseCase {

    private final CustomerPartnerEmployeeLinkRepository customerPartnerEmployeeLinkRepository;
    private final PartnerEmployeeRepository partnerEmployeeRepository;
    private final PartnerCompanyRepository partnerCompanyRepository;
    private final PartnerEmployeeImportBatchRepository importBatchRepository;
    private final Clock clock;

    public QueryCustomerPartnerEmployeeLinkService(
            CustomerPartnerEmployeeLinkRepository customerPartnerEmployeeLinkRepository,
            PartnerEmployeeRepository partnerEmployeeRepository,
            PartnerCompanyRepository partnerCompanyRepository,
            PartnerEmployeeImportBatchRepository importBatchRepository,
            Clock clock
    ) {
        this.customerPartnerEmployeeLinkRepository = customerPartnerEmployeeLinkRepository;
        this.partnerEmployeeRepository = partnerEmployeeRepository;
        this.partnerCompanyRepository = partnerCompanyRepository;
        this.importBatchRepository = importBatchRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerPartnerEmployeeEligibilityDto inspectEligibility(
            UUID customerId,
            UUID customerPartnerEmployeeLinkId
    ) {
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(customerPartnerEmployeeLinkId, "customerPartnerEmployeeLinkId must not be null");

        return customerPartnerEmployeeLinkRepository.findById(customerPartnerEmployeeLinkId)
                .filter(link -> link.customerId().equals(customerId))
                .map(this::assess)
                .orElseGet(() -> CustomerPartnerEmployeeEligibilityDto.ineligible(
                        CustomerPartnerEmployeeEligibilityDto.Status.NOT_VERIFIED
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerPartnerEmployeeEligibilityDto inspectCurrentEligibility(UUID customerId) {
        Objects.requireNonNull(customerId, "customerId must not be null");
        List<CustomerPartnerEmployeeLink> links = customerPartnerEmployeeLinkRepository.findByCustomerId(customerId);
        CustomerPartnerEmployeeEligibilityDto firstIneligible = null;
        for (CustomerPartnerEmployeeLink link : links) {
            CustomerPartnerEmployeeEligibilityDto assessment = assess(link);
            if (assessment.status() == CustomerPartnerEmployeeEligibilityDto.Status.ELIGIBLE) {
                return assessment;
            }
            if (firstIneligible == null) {
                firstIneligible = assessment;
            }
        }
        return firstIneligible == null
                ? CustomerPartnerEmployeeEligibilityDto.ineligible(
                        CustomerPartnerEmployeeEligibilityDto.Status.NOT_VERIFIED
                )
                : firstIneligible;
    }

    private CustomerPartnerEmployeeEligibilityDto assess(CustomerPartnerEmployeeLink link) {
        if (!link.isVerified()) {
            return ineligible(CustomerPartnerEmployeeEligibilityDto.Status.NOT_VERIFIED);
        }

        Optional<PartnerCompany> companyResult = partnerCompanyRepository.findById(link.partnerCompanyId());
        if (companyResult.isEmpty()
                || companyResult.orElseThrow().status() != PartnerCompanyStatus.ACTIVE) {
            return ineligible(CustomerPartnerEmployeeEligibilityDto.Status.PARTNER_INACTIVE);
        }

        Optional<PartnerEmployee> employeeResult = partnerEmployeeRepository.findById(link.partnerEmployeeId());
        if (employeeResult.isEmpty()
                || !employeeResult.orElseThrow().active()
                || employeeResult.orElseThrow().employmentStatus() != PartnerEmployeeStatus.ACTIVE) {
            return ineligible(CustomerPartnerEmployeeEligibilityDto.Status.EMPLOYEE_INACTIVE);
        }
        PartnerEmployee employee = employeeResult.orElseThrow();

        String currentEffectiveMonth = YearMonth.now(clock).toString();
        Optional<PartnerEmployeeImportBatch> authoritativeBatch = importBatchRepository
                .findLatestCompletedByPartnerCompanyIdAndEffectiveMonth(
                        link.partnerCompanyId(),
                        currentEffectiveMonth
                );
        if (authoritativeBatch.isEmpty()
                || !authoritativeBatch.orElseThrow().id().equals(link.sourceImportBatchId())
                || !authoritativeBatch.orElseThrow().partnerCompanyId().equals(link.partnerCompanyId())
                || !employee.partnerCompanyId().equals(link.partnerCompanyId())
                || !employee.importBatchId().equals(authoritativeBatch.orElseThrow().id())) {
            return ineligible(CustomerPartnerEmployeeEligibilityDto.Status.EVIDENCE_STALE);
        }

        return CustomerPartnerEmployeeEligibilityDto.eligible(
                new CustomerPartnerEmployeeLinkSnapshotDto(
                        link.customerId(),
                        link.id(),
                        link.partnerCompanyId(),
                        link.partnerEmployeeId(),
                        link.sourceImportBatchId(),
                        link.verificationOutcome().name(),
                        companyResult.orElseThrow().salaryAdvancePolicyLimit(),
                        employee.salaryAmount(),
                        employee.salaryAdvanceLimit(),
                        link.lastVerifiedAt(),
                        link.lastRefreshedAt()
                )
        );
    }

    private CustomerPartnerEmployeeEligibilityDto ineligible(
            CustomerPartnerEmployeeEligibilityDto.Status status
    ) {
        return CustomerPartnerEmployeeEligibilityDto.ineligible(status);
    }
}
