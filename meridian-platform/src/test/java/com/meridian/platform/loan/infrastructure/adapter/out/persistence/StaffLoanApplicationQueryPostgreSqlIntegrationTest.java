package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.dto.StaffLoanApplicationFilters;
import com.meridian.platform.loan.domain.model.OriginationChannel;
import com.meridian.platform.loan.application.port.out.LoanApplicationStatusTransitionRepository;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
        "meridian.loan.offer-expiry.enabled=false",
        "meridian.document.orphan-reconciliation.enabled=false"
})
@Transactional
class StaffLoanApplicationQueryPostgreSqlIntegrationTest {

    private static final String SCHEMA = "staff_case_query_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final LocalDateTime SHARED_SUBMISSION_TIME = LocalDateTime.of(
            2026, 9, 2, 8, 0
    );
    private static final UUID FIRST_ID = UUID.fromString(
            "00000000-0000-4000-8000-000000000001"
    );
    private static final UUID SECOND_ID = UUID.fromString(
            "00000000-0000-4000-8000-000000000002"
    );
    private static final UUID THIRD_ID = UUID.fromString(
            "00000000-0000-4000-8000-000000000003"
    );

    @Autowired JdbcTemplate jdbc;
    @Autowired LoanApplicationRepository applications;
    @Autowired LoanApplicationStatusTransitionRepository transitions;
    @Autowired com.meridian.platform.loan.application.port.in.QueryStaffLoanApplicationsUseCase cases;
    @Autowired com.meridian.platform.identity.application.port.in.QueryWorkflowActorSummariesUseCase actors;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.meridian.platform.shared.application.security.CurrentUserProvider currentUsers;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add(
                "spring.datasource.hikari.connection-init-sql",
                () -> "SET search_path TO " + SCHEMA
        );
    }

    @BeforeEach
    void insertApplications() {
        insertApplication(
                FIRST_ID,
                "SA-20260902-000001",
                "SALARY_ADVANCE",
                "REJECTED",
                SHARED_SUBMISSION_TIME
        );
        insertApplication(
                SECOND_ID,
                "UCL-20260902-000002",
                "UNSECURED_CONSUMER_LOAN",
                "REJECTED",
                SHARED_SUBMISSION_TIME
        );
        insertApplication(
                THIRD_ID,
                "CL-20260902-000003",
                "COLLATERAL_LOAN",
                "REJECTED",
                SHARED_SUBMISSION_TIME
        );
        insertApplication(
                UUID.fromString("00000000-0000-4000-8000-000000000004"),
                "UCL-20260901-000004",
                "UNSECURED_CONSUMER_LOAN",
                "EXPIRED",
                SHARED_SUBMISSION_TIME.minusDays(1)
        );
    }

    @Test
    void pagesAllProductsWithStableSubmittedAtThenIdOrderingAndMetadata() {
        LoanApplicationRepository.StaffPage firstPage = applications.findStaffPage(
                null,
                null,
                0,
                2
        );
        LoanApplicationRepository.StaffPage secondPage = applications.findStaffPage(
                null,
                null,
                1,
                2
        );

        assertEquals(4, firstPage.totalElements());
        assertEquals(2, firstPage.totalPages());
        assertEquals(THIRD_ID, firstPage.applications().getFirst().id());
        assertEquals(SECOND_ID, firstPage.applications().getLast().id());
        assertEquals(FIRST_ID, secondPage.applications().getFirst().id());
        assertEquals(
                ProductCode.UNSECURED_CONSUMER_LOAN,
                secondPage.applications().getLast().productCode()
        );
    }

    @Test
    void appliesProductStatusAndCombinedFiltersIncludingEmptyPages() {
        assertEquals(2, applications.findStaffPage(
                ProductCode.UNSECURED_CONSUMER_LOAN,
                null,
                0,
                20
        ).totalElements());
        assertEquals(3, applications.findStaffPage(
                null,
                LoanApplicationStatus.REJECTED,
                0,
                20
        ).totalElements());

        LoanApplicationRepository.StaffPage combined = applications.findStaffPage(
                ProductCode.UNSECURED_CONSUMER_LOAN,
                LoanApplicationStatus.REJECTED,
                0,
                20
        );
        assertEquals(1, combined.totalElements());
        assertEquals(SECOND_ID, combined.applications().getFirst().id());

        LoanApplicationRepository.StaffPage empty = applications.findStaffPage(
                ProductCode.SALARY_ADVANCE,
                LoanApplicationStatus.EXPIRED,
                0,
                20
        );
        assertEquals(0, empty.totalElements());
        assertEquals(0, empty.totalPages());
        assertEquals(0, empty.applications().size());
    }

    @Test
    void exactReferenceAndAllPredicatesComposeBeforePagingWithoutWrites() {
        staffReader();
        long auditBefore = jdbc.queryForObject("select count(*) from " + SCHEMA + ".audit_events", Long.class);
        var rowsBefore = jdbc.queryForList("select * from " + SCHEMA + ".loan_applications order by id");
        var transitionsBefore = jdbc.queryForList("select * from " + SCHEMA + ".loan_application_status_transitions order by id");
        var filters = new StaffLoanApplicationFilters(ProductCode.UNSECURED_CONSUMER_LOAN,
                LoanApplicationStatus.REJECTED, " ucl-20260902-000002 ", OriginationChannel.CUSTOMER_DIGITAL,
                LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 2));
        var result = cases.queryApplications(filters, 0, 1);
        assertEquals(1, result.totalElements());
        assertEquals(1, result.totalPages());
        assertEquals(SECOND_ID, result.items().getFirst().loanApplicationId());
        assertEquals("CUSTOMER_DIGITAL", result.items().getFirst().originationChannel());
        var beyond = cases.queryApplications(filters, 1, 1);
        assertEquals(1, beyond.totalElements());
        assertEquals(0, beyond.items().size());
        assertEquals(0, cases.queryApplications(new StaffLoanApplicationFilters(null, null,
                "UCL-20260902-999999", null, null, null), 0, 20).totalElements());
        assertEquals(0, cases.queryApplications(new StaffLoanApplicationFilters(ProductCode.SALARY_ADVANCE,
                null, "UCL-20260902-000002", null, null, null), 0, 20).totalElements());
        assertEquals(4, cases.queryApplications(new StaffLoanApplicationFilters(null, null, " ", null, null, null), 0, 20).totalElements());
        assertEquals(rowsBefore, jdbc.queryForList("select * from " + SCHEMA + ".loan_applications order by id"));
        assertEquals(transitionsBefore, jdbc.queryForList("select * from " + SCHEMA + ".loan_application_status_transitions order by id"));
        assertEquals(auditBefore, jdbc.queryForObject("select count(*) from " + SCHEMA + ".audit_events", Long.class));
    }

    @Test
    void channelValuesFilterPersistedFactsIncludingIncompatibleSalaryAdvance() {
        UUID assistedId = UUID.randomUUID();
        insertApplication(assistedId, "UCL-20260902-000005", "UNSECURED_CONSUMER_LOAN", "REJECTED",
                SHARED_SUBMISSION_TIME, "STAFF_ASSISTED");
        var assisted = applications.findStaffPage(new StaffLoanApplicationFilters(null, null, null,
                OriginationChannel.STAFF_ASSISTED, null, null), 0, 20);
        assertEquals(1, assisted.totalElements());
        assertEquals(assistedId, assisted.applications().getFirst().id());
        assertEquals(4, applications.findStaffPage(new StaffLoanApplicationFilters(null, null, null,
                OriginationChannel.CUSTOMER_DIGITAL, null, null), 0, 20).totalElements());
        assertEquals(0, applications.findStaffPage(new StaffLoanApplicationFilters(ProductCode.SALARY_ADVANCE,
                null, null, OriginationChannel.STAFF_ASSISTED, null, null), 0, 20).totalElements());
    }

    @Test
    void displayDayBoundsAreInclusiveOpenEndedAndPreserveStableCountsAndOrder() {
        LocalDate day = LocalDate.of(2026, 9, 2);
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 17, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 2, 17, 0);
        UUID atStart = UUID.randomUUID();
        UUID lastMicrosecond = UUID.randomUUID();
        insertApplication(UUID.randomUUID(), "CL-20260901-000010", "COLLATERAL_LOAN", "REJECTED", start.minusNanos(1000));
        insertApplication(atStart, "CL-20260901-000011", "COLLATERAL_LOAN", "REJECTED", start);
        insertApplication(lastMicrosecond, "CL-20260902-000012", "COLLATERAL_LOAN", "REJECTED", end.minusNanos(1000));
        insertApplication(UUID.randomUUID(), "CL-20260902-000013", "COLLATERAL_LOAN", "REJECTED", end);
        var filters = new StaffLoanApplicationFilters(null, null, null, null, day, day);
        var first = applications.findStaffPage(filters, 0, 2);
        var second = applications.findStaffPage(filters, 1, 2);
        var third = applications.findStaffPage(filters, 2, 2);
        assertEquals(5, first.totalElements());
        assertEquals(3, first.totalPages());
        assertEquals(List.of(lastMicrosecond, THIRD_ID), first.applications().stream().map(a -> a.id()).toList());
        assertEquals(List.of(SECOND_ID, FIRST_ID), second.applications().stream().map(a -> a.id()).toList());
        assertEquals(atStart, third.applications().getFirst().id());
        assertEquals(6, applications.findStaffPage(new StaffLoanApplicationFilters(null, null, null, null, day, null), 0, 20).totalElements());
        assertEquals(7, applications.findStaffPage(new StaffLoanApplicationFilters(null, null, null, null, null, day), 0, 20).totalElements());
        assertEquals(8, applications.findStaffPage(null, null, 0, 20).totalElements());
        staffReader();
        assertThrows(IllegalArgumentException.class, () -> cases.queryApplications(
                new StaffLoanApplicationFilters(null, null, null, null, day.plusDays(1), day), 0, 20));
    }

    private void staffReader() {
        org.mockito.Mockito.when(currentUsers.currentUser()).thenReturn(
                new com.meridian.platform.shared.application.security.AuthenticatedUser(UUID.randomUUID(),
                        "staff@meridian.local", "STAFF", null, Set.of("LOAN_OFFICER"), Set.of("loan:read")));
    }

    @Test
    void loadsImmutableTransitionEvidenceByAuthoritativeSequence() {
        jdbc.update(
                "insert into " + SCHEMA + ".loan_application_status_transitions "
                        + "(id,loan_application_id,operation_id,sequence_number,from_status,"
                        + "to_status,action,reason,actor_type,actor_user_id,occurred_at) "
                        + "values (?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                FIRST_ID,
                UUID.randomUUID(),
                2,
                "SUBMITTED",
                "REJECTED",
                "REJECT",
                "restricted decision note",
                "SYSTEM",
                null,
                SHARED_SUBMISSION_TIME.plusHours(1)
        );
        jdbc.update(
                "insert into " + SCHEMA + ".loan_application_status_transitions "
                        + "(id,loan_application_id,operation_id,sequence_number,from_status,"
                        + "to_status,action,reason,actor_type,actor_user_id,occurred_at) "
                        + "values (?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                FIRST_ID,
                UUID.randomUUID(),
                1,
                null,
                "SUBMITTED",
                "SUBMIT_APPLICATION",
                null,
                "SYSTEM",
                null,
                SHARED_SUBMISSION_TIME
        );

        var result = transitions.findByLoanApplicationIdOrderBySequenceNumberAsc(FIRST_ID);

        assertEquals(2, result.size());
        assertEquals(1, result.getFirst().sequenceNumber());
        assertNull(result.getFirst().fromStatus());
        assertEquals("SUBMIT_APPLICATION", result.getFirst().action().name());
        assertEquals(2, result.getLast().sequenceNumber());
        assertEquals("restricted decision note", result.getLast().reason());
    }

    @Test
    void realCustomerAssociationAndPersistedSubmissionResolveSafelyWithoutReadEffects() {
        Map<String, Object> customerUser = jdbc.queryForMap("select id, customer_id, email from " + SCHEMA
                + ".users where user_type = 'CUSTOMER' order by id limit 1");
        UUID userId = (UUID) customerUser.get("id");
        UUID customerId = (UUID) customerUser.get("customer_id");
        jdbc.update("update " + SCHEMA + ".loan_applications set customer_id=? where id=?", customerId, FIRST_ID);
        transitions.save(new com.meridian.platform.loan.domain.model.LoanApplicationStatusTransition(
                UUID.randomUUID(), FIRST_ID, UUID.randomUUID(), 1, null, LoanApplicationStatus.SUBMITTED,
                com.meridian.platform.loan.domain.model.LoanApplicationTransitionAction.SUBMIT_APPLICATION, null,
                com.meridian.platform.shared.domain.model.ActorType.USER, userId, SHARED_SUBMISSION_TIME));
        org.mockito.Mockito.when(currentUsers.currentUser()).thenReturn(
                new com.meridian.platform.shared.application.security.AuthenticatedUser(UUID.randomUUID(),
                        "staff@meridian.local", "STAFF", null, java.util.Set.of("LOAN_OFFICER"), java.util.Set.of("loan:read")));
        long before = jdbc.queryForObject("select count(*) from " + SCHEMA + ".audit_events", Long.class);
        var result = cases.queryCase(FIRST_ID);
        assertEquals("CUSTOMER_SELF_SERVICE", result.lifecycleHistory().getFirst().actorType());
        assertNull(result.lifecycleHistory().getFirst().actor());
        var classification = actors.findByUserIds(java.util.Set.of(userId)).get(userId);
        assertEquals(customerId, classification.customerId());
        assertNull(classification.staff());
        String json = tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build().writeValueAsString(result);
        org.junit.jupiter.api.Assertions.assertFalse(json.contains(userId.toString()));
        org.junit.jupiter.api.Assertions.assertFalse(json.contains((String) customerUser.get("email")));
        assertEquals(before, jdbc.queryForObject("select count(*) from " + SCHEMA + ".audit_events", Long.class));
        assertEquals(1, transitions.findByLoanApplicationIdOrderBySequenceNumberAsc(FIRST_ID).size());
    }

    private void insertApplication(
            UUID applicationId,
            String applicationNumber,
            String productCode,
            String status,
            LocalDateTime submittedAt
    ) {
        insertApplication(applicationId, applicationNumber, productCode, status, submittedAt, "CUSTOMER_DIGITAL");
    }

    private void insertApplication(UUID applicationId, String applicationNumber, String productCode,
            String status, LocalDateTime submittedAt, String originationChannel) {
        Map<String, Object> product = jdbc.queryForMap(
                "select id, product_type from " + SCHEMA
                        + ".loan_products where product_code = ?",
                productCode
        );
        UUID customerId = jdbc.queryForObject(
                "select id from " + SCHEMA + ".customers order by id limit 1",
                UUID.class
        );
        jdbc.update(
                "insert into " + SCHEMA + ".loan_applications "
                        + "(id,customer_id,loan_product_id,application_number,product_code,"
                        + "product_type,status,requested_amount,requested_term_months,submitted_at,origination_channel) "
                        + "values (?,?,?,?,?,?,?,?,?,?,?)",
                applicationId,
                customerId,
                product.get("id"),
                applicationNumber,
                productCode,
                product.get("product_type"),
                status,
                3_000_000,
                3,
                submittedAt,
                originationChannel
        );
    }
}
