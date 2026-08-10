package com.meridian.platform.partner.application.service;

import com.meridian.platform.partner.application.dto.CustomerPartnerEmployeeEligibilityDto;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryCustomerPartnerEmployeeLinkServiceTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID LINK_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID COMPANY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EMPLOYEE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01");
    private static final UUID BATCH_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
    private static final UUID NEWER_BATCH_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2");

    private FakeLinkRepository links;
    private FakeEmployeeRepository employees;
    private FakeCompanyRepository companies;
    private FakeImportBatchRepository batches;

    @BeforeEach
    void setUp() {
        links = new FakeLinkRepository(verifiedLink(BATCH_ID, EMPLOYEE_ID));
        employees = new FakeEmployeeRepository(activeEmployee(BATCH_ID, EMPLOYEE_ID));
        companies = new FakeCompanyRepository(PartnerCompanyStatus.ACTIVE);
        batches = new FakeImportBatchRepository(completedBatch(BATCH_ID, "2026-08"));
    }

    @Test
    void currentAuthoritativeEvidenceIsEligibleAtLastInstantOfMonth() {
        QueryCustomerPartnerEmployeeLinkService service = serviceAt("2026-08-31T23:59:59.999999Z");

        CustomerPartnerEmployeeEligibilityDto result = service.inspectEligibility(CUSTOMER_ID, LINK_ID);

        assertEquals(CustomerPartnerEmployeeEligibilityDto.Status.ELIGIBLE, result.status());
        assertEquals(LINK_ID, result.optionalSnapshot().orElseThrow().customerPartnerEmployeeLinkId());
    }

    @Test
    void previousMonthEvidenceFailsClosedAtFirstInstantOfNextMonth() {
        QueryCustomerPartnerEmployeeLinkService service = serviceAt("2026-09-01T00:00:00Z");

        CustomerPartnerEmployeeEligibilityDto result = service.inspectEligibility(CUSTOMER_ID, LINK_ID);

        assertEquals(CustomerPartnerEmployeeEligibilityDto.Status.EVIDENCE_STALE, result.status());
        assertTrue(result.optionalSnapshot().isEmpty());
    }

    @Test
    void newerAuthoritativeCurrentMonthBatchInvalidatesOlderLink() {
        batches.authoritativeBatch = Optional.of(completedBatch(NEWER_BATCH_ID, "2026-08"));

        CustomerPartnerEmployeeEligibilityDto result = serviceAt("2026-08-10T00:00:00Z")
                .inspectEligibility(CUSTOMER_ID, LINK_ID);

        assertEquals(CustomerPartnerEmployeeEligibilityDto.Status.EVIDENCE_STALE, result.status());
    }

    @Test
    void refreshedLinkAndEmployeeUsingNewAuthoritativeBatchRecoverEligibility() {
        batches.authoritativeBatch = Optional.of(completedBatch(NEWER_BATCH_ID, "2026-08"));
        links.current = verifiedLink(NEWER_BATCH_ID, EMPLOYEE_ID);
        links.all = List.of(links.current);
        employees.current = activeEmployee(NEWER_BATCH_ID, EMPLOYEE_ID);
        employees.all.clear();
        employees.all.add(employees.current);

        CustomerPartnerEmployeeEligibilityDto result = serviceAt("2026-08-10T00:00:00Z")
                .inspectEligibility(CUSTOMER_ID, LINK_ID);

        assertEquals(CustomerPartnerEmployeeEligibilityDto.Status.ELIGIBLE, result.status());
    }

    @Test
    void inactiveCompanyAndEmployeeRemainHardStops() {
        companies.status = PartnerCompanyStatus.INACTIVE;
        assertEquals(
                CustomerPartnerEmployeeEligibilityDto.Status.PARTNER_INACTIVE,
                serviceAt("2026-08-10T00:00:00Z").inspectEligibility(CUSTOMER_ID, LINK_ID).status()
        );

        companies.status = PartnerCompanyStatus.ACTIVE;
        employees.current = new PartnerEmployee(
                EMPLOYEE_ID, COMPANY_ID, BATCH_ID, "MER-EMP-001", "IDREF-MER-001",
                amount(18_000_000), amount(6_000_000), PartnerEmployeeStatus.INACTIVE, false
        );
        employees.all.clear();
        employees.all.add(employees.current);
        assertEquals(
                CustomerPartnerEmployeeEligibilityDto.Status.EMPLOYEE_INACTIVE,
                serviceAt("2026-08-10T00:00:00Z").inspectEligibility(CUSTOMER_ID, LINK_ID).status()
        );
    }

    @Test
    void currentEligibilityReturnsTheFirstEligibleVerifiedLinkWithoutExposingStaleSnapshot() {
        UUID staleLinkId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccb");
        UUID staleEmployeeId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb02");
        links.all = List.of(
                verifiedLink(staleLinkId, NEWER_BATCH_ID, staleEmployeeId),
                verifiedLink(LINK_ID, BATCH_ID, EMPLOYEE_ID)
        );
        employees.all.add(activeEmployee(NEWER_BATCH_ID, staleEmployeeId));
        employees.all.add(employees.current);

        CustomerPartnerEmployeeEligibilityDto result = serviceAt("2026-08-10T00:00:00Z")
                .inspectCurrentEligibility(CUSTOMER_ID);

        assertEquals(CustomerPartnerEmployeeEligibilityDto.Status.ELIGIBLE, result.status());
        assertEquals(LINK_ID, result.optionalSnapshot().orElseThrow().customerPartnerEmployeeLinkId());
    }

    private QueryCustomerPartnerEmployeeLinkService serviceAt(String instant) {
        return new QueryCustomerPartnerEmployeeLinkService(
                links,
                employees,
                companies,
                batches,
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC)
        );
    }

    private static CustomerPartnerEmployeeLink verifiedLink(UUID batchId, UUID employeeId) {
        return verifiedLink(LINK_ID, batchId, employeeId);
    }

    private static CustomerPartnerEmployeeLink verifiedLink(UUID linkId, UUID batchId, UUID employeeId) {
        return new CustomerPartnerEmployeeLink(
                linkId,
                CUSTOMER_ID,
                COMPANY_ID,
                employeeId,
                batchId,
                EmployeeVerificationOutcome.MATCHED_ACTIVE,
                CustomerPartnerEmployeeLinkStatus.VERIFIED,
                "IDREF-MER-001",
                "MER-EMP-001",
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 1, 0, 0)
        );
    }

    private static PartnerEmployee activeEmployee(UUID batchId, UUID employeeId) {
        return new PartnerEmployee(
                employeeId,
                COMPANY_ID,
                batchId,
                "MER-EMP-001",
                "IDREF-MER-001",
                amount(18_000_000),
                amount(6_000_000),
                PartnerEmployeeStatus.ACTIVE,
                true
        );
    }

    private static PartnerEmployeeImportBatch completedBatch(UUID id, String effectiveMonth) {
        return new PartnerEmployeeImportBatch(
                id,
                COMPANY_ID,
                effectiveMonth,
                PartnerEmployeeImportBatchStatus.COMPLETED,
                1,
                0
        );
    }

    private static BigDecimal amount(long value) {
        return BigDecimal.valueOf(value).setScale(2);
    }

    private static final class FakeLinkRepository implements CustomerPartnerEmployeeLinkRepository {
        private CustomerPartnerEmployeeLink current;
        private List<CustomerPartnerEmployeeLink> all;

        private FakeLinkRepository(CustomerPartnerEmployeeLink current) {
            this.current = current;
            this.all = List.of(current);
        }

        @Override
        public Optional<CustomerPartnerEmployeeLink> findById(UUID id) {
            return all.stream().filter(link -> link.id().equals(id)).findFirst();
        }

        @Override
        public Optional<CustomerPartnerEmployeeLink> findCurrentByCustomerIdAndPartnerCompanyId(
                UUID customerId,
                UUID partnerCompanyId
        ) {
            return all.stream()
                    .filter(link -> link.customerId().equals(customerId))
                    .filter(link -> link.partnerCompanyId().equals(partnerCompanyId))
                    .findFirst();
        }

        @Override
        public List<CustomerPartnerEmployeeLink> findByCustomerId(UUID customerId) {
            return all.stream().filter(link -> link.customerId().equals(customerId)).toList();
        }

        @Override
        public CustomerPartnerEmployeeLink save(CustomerPartnerEmployeeLink link) {
            current = link;
            all = List.of(link);
            return link;
        }
    }

    private static final class FakeEmployeeRepository implements PartnerEmployeeRepository {
        private PartnerEmployee current;
        private final List<PartnerEmployee> all = new ArrayList<>();

        private FakeEmployeeRepository(PartnerEmployee current) {
            this.current = current;
            this.all.add(current);
        }

        @Override
        public Optional<PartnerEmployee> findById(UUID id) {
            return all.stream().filter(employee -> employee.id().equals(id)).findFirst()
                    .or(() -> Optional.ofNullable(current).filter(employee -> employee.id().equals(id)));
        }

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
            return List.of();
        }
    }

    private static final class FakeCompanyRepository implements PartnerCompanyRepository {
        private PartnerCompanyStatus status;

        private FakeCompanyRepository(PartnerCompanyStatus status) {
            this.status = status;
        }

        @Override
        public List<PartnerCompany> findAll() {
            return List.of();
        }

        @Override
        public Optional<PartnerCompany> findById(UUID id) {
            if (!COMPANY_ID.equals(id)) {
                return Optional.empty();
            }
            return Optional.of(new PartnerCompany(
                    COMPANY_ID,
                    "MERIDIAN_PARTNER",
                    "Meridian Partner Co.",
                    status,
                    amount(10_000_000)
            ));
        }
    }

    private static final class FakeImportBatchRepository implements PartnerEmployeeImportBatchRepository {
        private Optional<PartnerEmployeeImportBatch> authoritativeBatch;

        private FakeImportBatchRepository(PartnerEmployeeImportBatch authoritativeBatch) {
            this.authoritativeBatch = Optional.of(authoritativeBatch);
        }

        @Override
        public List<PartnerEmployeeImportBatch> findByPartnerCompanyId(UUID partnerCompanyId) {
            return authoritativeBatch.stream().toList();
        }

        @Override
        public Optional<PartnerEmployeeImportBatch> findLatestCompletedByPartnerCompanyId(UUID partnerCompanyId) {
            return authoritativeBatch;
        }

        @Override
        public Optional<PartnerEmployeeImportBatch> findLatestCompletedByPartnerCompanyIdAndEffectiveMonth(
                UUID partnerCompanyId,
                String effectiveMonth
        ) {
            return authoritativeBatch
                    .filter(batch -> batch.partnerCompanyId().equals(partnerCompanyId))
                    .filter(batch -> batch.effectiveMonth().equals(effectiveMonth));
        }
    }
}
