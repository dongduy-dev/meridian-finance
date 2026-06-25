package com.meridian.platform.partner.application.service;

import com.meridian.platform.partner.application.dto.PartnerEmployeeVerificationDto;
import com.meridian.platform.partner.application.dto.PartnerEmployeeVerificationRequest;
import com.meridian.platform.partner.application.mapper.PartnerEmployeeVerificationMapper;
import com.meridian.platform.partner.application.port.out.CustomerPartnerEmployeeLinkRepository;
import com.meridian.platform.partner.application.port.out.PartnerCompanyRepository;
import com.meridian.platform.partner.application.port.out.PartnerEmployeeImportBatchRepository;
import com.meridian.platform.partner.application.port.out.PartnerEmployeeRepository;
import com.meridian.platform.partner.domain.model.CustomerPartnerEmployeeLink;
import com.meridian.platform.partner.domain.model.CustomerPartnerEmployeeLinkStatus;
import com.meridian.platform.partner.domain.model.EmployeeVerificationOutcome;
import com.meridian.platform.partner.domain.model.PartnerCompany;
import com.meridian.platform.partner.domain.model.PartnerCompanyStatus;
import com.meridian.platform.partner.domain.model.PartnerEmployee;
import com.meridian.platform.partner.domain.model.PartnerEmployeeImportBatch;
import com.meridian.platform.partner.domain.model.PartnerEmployeeImportBatchStatus;
import com.meridian.platform.partner.domain.model.PartnerEmployeeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerifyPartnerEmployeeServiceTest {

    private final UUID customerId = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private final UUID partnerCompanyId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID importBatchId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
    private final UUID partnerEmployeeId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01");

    private FakePartnerCompanyRepository partnerCompanyRepository;
    private FakePartnerEmployeeImportBatchRepository importBatchRepository;
    private FakePartnerEmployeeRepository partnerEmployeeRepository;
    private FakeCustomerPartnerEmployeeLinkRepository linkRepository;
    private VerifyPartnerEmployeeService service;

    @BeforeEach
    void setUp() {
        partnerCompanyRepository = new FakePartnerCompanyRepository(partnerCompanyId);
        importBatchRepository = new FakePartnerEmployeeImportBatchRepository(importBatchId, partnerCompanyId);
        partnerEmployeeRepository = new FakePartnerEmployeeRepository();
        linkRepository = new FakeCustomerPartnerEmployeeLinkRepository();
        service = new VerifyPartnerEmployeeService(
                partnerCompanyRepository,
                importBatchRepository,
                partnerEmployeeRepository,
                linkRepository,
                new PartnerEmployeeVerificationMapper()
        );
    }

    @Test
    void createsVerifiedLinkForMatchedActiveEmployee() {
        partnerEmployeeRepository.employees.add(activeEmployee());

        PartnerEmployeeVerificationDto result = service.verifyPartnerEmployee(
                partnerCompanyId,
                new PartnerEmployeeVerificationRequest(customerId, " IDREF-MER-001 ", " MER-EMP-001 ")
        );

        assertEquals("MATCHED_ACTIVE", result.outcome());
        assertEquals("VERIFIED", result.linkStatus());
        assertFalse(result.manualReviewRequired());
        assertEquals(partnerEmployeeId, result.partnerEmployeeId());
        assertNotNull(result.customerPartnerEmployeeLinkId());
        assertEquals(BigDecimal.valueOf(18_000_000).setScale(2), result.salaryAmount());
        assertEquals(BigDecimal.valueOf(6_000_000).setScale(2), result.salaryAdvanceLimit());
        assertEquals("IDREF-MER-001", linkRepository.savedLink.verifiedIdentityRef());
        assertEquals("MER-EMP-001", linkRepository.savedLink.verifiedEmployeeCode());
    }

    @Test
    void returnsPendingManualReviewWhenNoCompletedImportBatchExists() {
        importBatchRepository.latestCompletedBatch = Optional.empty();

        PartnerEmployeeVerificationDto result = service.verifyPartnerEmployee(
                partnerCompanyId,
                new PartnerEmployeeVerificationRequest(customerId, "IDREF-MER-001", "MER-EMP-001")
        );

        assertEquals("PENDING_MANUAL_REVIEW", result.outcome());
        assertTrue(result.manualReviewRequired());
        assertNull(result.partnerEmployeeId());
        assertNull(result.customerPartnerEmployeeLinkId());
        assertNull(linkRepository.savedLink);
    }

    @Test
    void returnsMatchedInactiveWithoutCreatingLink() {
        partnerEmployeeRepository.employees.add(inactiveEmployee());

        PartnerEmployeeVerificationDto result = service.verifyPartnerEmployee(
                partnerCompanyId,
                new PartnerEmployeeVerificationRequest(customerId, "IDREF-MER-001", "MER-EMP-001")
        );

        assertEquals("MATCHED_INACTIVE", result.outcome());
        assertFalse(result.manualReviewRequired());
        assertEquals(partnerEmployeeId, result.partnerEmployeeId());
        assertNull(result.customerPartnerEmployeeLinkId());
        assertNull(result.salaryAmount());
        assertNull(linkRepository.savedLink);
    }

    @Test
    void doesNotOverwriteSuspendedExistingLink() {
        partnerEmployeeRepository.employees.add(activeEmployee());
        CustomerPartnerEmployeeLink existingLink = existingLink(CustomerPartnerEmployeeLinkStatus.SUSPENDED);
        linkRepository.currentLink = Optional.of(existingLink);

        PartnerEmployeeVerificationDto result = service.verifyPartnerEmployee(
                partnerCompanyId,
                new PartnerEmployeeVerificationRequest(customerId, "IDREF-MER-001", "MER-EMP-001")
        );

        assertEquals("PENDING_MANUAL_REVIEW", result.outcome());
        assertEquals("SUSPENDED", result.linkStatus());
        assertTrue(result.manualReviewRequired());
        assertEquals(existingLink.id(), result.customerPartnerEmployeeLinkId());
        assertNull(result.salaryAmount());
        assertNull(linkRepository.savedLink);
    }

    private PartnerEmployee activeEmployee() {
        return employee(PartnerEmployeeStatus.ACTIVE, true);
    }

    private PartnerEmployee inactiveEmployee() {
        return employee(PartnerEmployeeStatus.INACTIVE, false);
    }

    private PartnerEmployee employee(PartnerEmployeeStatus status, boolean active) {
        return new PartnerEmployee(
                partnerEmployeeId,
                partnerCompanyId,
                importBatchId,
                "MER-EMP-001",
                "IDREF-MER-001",
                BigDecimal.valueOf(18_000_000).setScale(2),
                BigDecimal.valueOf(6_000_000).setScale(2),
                status,
                active
        );
    }

    private CustomerPartnerEmployeeLink existingLink(CustomerPartnerEmployeeLinkStatus status) {
        return new CustomerPartnerEmployeeLink(
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                customerId,
                partnerCompanyId,
                partnerEmployeeId,
                importBatchId,
                EmployeeVerificationOutcome.MATCHED_ACTIVE,
                status,
                "IDREF-MER-001",
                "MER-EMP-001",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    private static class FakePartnerCompanyRepository implements PartnerCompanyRepository {

        private final UUID partnerCompanyId;

        private FakePartnerCompanyRepository(UUID partnerCompanyId) {
            this.partnerCompanyId = partnerCompanyId;
        }

        @Override
        public List<PartnerCompany> findAll() {
            return List.of();
        }

        @Override
        public Optional<PartnerCompany> findById(UUID partnerCompanyId) {
            if (!this.partnerCompanyId.equals(partnerCompanyId)) {
                return Optional.empty();
            }

            return Optional.of(new PartnerCompany(
                    partnerCompanyId,
                    "MERIDIAN_PARTNER",
                    "Meridian Partner Co.",
                    PartnerCompanyStatus.ACTIVE,
                    BigDecimal.valueOf(20_000_000).setScale(2)
            ));
        }
    }

    private static class FakePartnerEmployeeImportBatchRepository implements PartnerEmployeeImportBatchRepository {

        private Optional<PartnerEmployeeImportBatch> latestCompletedBatch;

        private FakePartnerEmployeeImportBatchRepository(UUID importBatchId, UUID partnerCompanyId) {
            latestCompletedBatch = Optional.of(new PartnerEmployeeImportBatch(
                    importBatchId,
                    partnerCompanyId,
                    "2026-06",
                    PartnerEmployeeImportBatchStatus.COMPLETED,
                    1,
                    0
            ));
        }

        @Override
        public List<PartnerEmployeeImportBatch> findByPartnerCompanyId(UUID partnerCompanyId) {
            return latestCompletedBatch.stream().toList();
        }

        @Override
        public Optional<PartnerEmployeeImportBatch> findLatestCompletedByPartnerCompanyId(UUID partnerCompanyId) {
            return latestCompletedBatch.filter(batch -> batch.partnerCompanyId().equals(partnerCompanyId));
        }
    }

    private static class FakePartnerEmployeeRepository implements PartnerEmployeeRepository {

        private final List<PartnerEmployee> employees = new ArrayList<>();

        @Override
        public List<PartnerEmployee> findByPartnerCompanyId(UUID partnerCompanyId) {
            return List.of();
        }

        @Override
        public List<PartnerEmployee> findActiveByPartnerCompanyId(UUID companyId) {
            return List.of();
        }

        @Override
        public List<PartnerEmployee> findByVerificationEvidence(
                UUID partnerCompanyId,
                UUID importBatchId,
                String identityReference,
                String employeeCode
        ) {
            return employees.stream()
                    .filter(employee -> employee.partnerCompanyId().equals(partnerCompanyId))
                    .filter(employee -> employee.importBatchId().equals(importBatchId))
                    .filter(employee -> employee.identityReference().equals(identityReference))
                    .filter(employee -> employee.employeeCode().equals(employeeCode))
                    .toList();
        }
    }

    private static class FakeCustomerPartnerEmployeeLinkRepository implements CustomerPartnerEmployeeLinkRepository {

        private Optional<CustomerPartnerEmployeeLink> currentLink = Optional.empty();
        private CustomerPartnerEmployeeLink savedLink;

        @Override
        public Optional<CustomerPartnerEmployeeLink> findCurrentByCustomerIdAndPartnerCompanyId(
                UUID customerId,
                UUID partnerCompanyId
        ) {
            return currentLink.filter(link ->
                    link.customerId().equals(customerId) && link.partnerCompanyId().equals(partnerCompanyId)
            );
        }

        @Override
        public CustomerPartnerEmployeeLink save(CustomerPartnerEmployeeLink customerPartnerEmployeeLink) {
            savedLink = customerPartnerEmployeeLink;
            currentLink = Optional.of(customerPartnerEmployeeLink);
            return customerPartnerEmployeeLink;
        }
    }
}
