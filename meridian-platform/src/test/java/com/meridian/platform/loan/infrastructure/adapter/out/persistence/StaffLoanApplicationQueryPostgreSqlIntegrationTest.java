package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
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

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
                "SA-CP2-000001",
                "SALARY_ADVANCE",
                "REJECTED",
                SHARED_SUBMISSION_TIME
        );
        insertApplication(
                SECOND_ID,
                "UCL-CP2-000002",
                "UNSECURED_CONSUMER_LOAN",
                "REJECTED",
                SHARED_SUBMISSION_TIME
        );
        insertApplication(
                THIRD_ID,
                "CL-CP2-000003",
                "COLLATERAL_LOAN",
                "REJECTED",
                SHARED_SUBMISSION_TIME
        );
        insertApplication(
                UUID.fromString("00000000-0000-4000-8000-000000000004"),
                "UCL-CP2-000004",
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
                        + "product_type,status,requested_amount,requested_term_months,submitted_at) "
                        + "values (?,?,?,?,?,?,?,?,?,?)",
                applicationId,
                customerId,
                product.get("id"),
                applicationNumber,
                productCode,
                product.get("product_type"),
                status,
                3_000_000,
                3,
                submittedAt
        );
    }
}
