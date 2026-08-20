package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualDisbursementAuditV29MigrationTest {

    private static final Path MIGRATION_DIRECTORY =
            Path.of("src/main/resources/db/migration");
    private static final String V28_NORMALIZED_SHA256 =
            "7812CB8353C30A5794B2977E4E8CE51CA68AA11605ADF90660FCD6C9D988BAA2";

    @Test
    void extendsOnlyTheNamedAuditActionConstraint() throws Exception {
        String sql = Files.readString(MIGRATION_DIRECTORY.resolve(
                "V29__add_manual_disbursement_audit_action.sql"
        ));

        assertTrue(sql.contains("V29 preflight failed"));
        assertTrue(sql.contains("regexp_matches"));
        assertTrue(sql.contains("DROP CONSTRAINT chk_audit_events_action"));
        assertTrue(sql.contains("ADD CONSTRAINT chk_audit_events_action CHECK"));
        assertTrue(sql.contains("'MANUAL_DISBURSEMENT_CONFIRMED'"));
        for (BusinessAuditAction action : BusinessAuditAction.values()) {
            if (action == BusinessAuditAction
                    .LOAN_CONTRACT_DISBURSEMENT_DESTINATION_REVEALED
                    || action == BusinessAuditAction.UNSECURED_CONSUMER_LOAN_APPLICATION_SUBMITTED
                    || action == BusinessAuditAction.COLLATERAL_LOAN_APPLICATION_SUBMITTED
                    || action == BusinessAuditAction.UNSECURED_CONSUMER_LOAN_VERIFICATION_STARTED
                    || action == BusinessAuditAction.UNSECURED_CONSUMER_LOAN_VERIFICATION_COMPLETED
                    || action == BusinessAuditAction.COLLATERAL_LOAN_VERIFICATION_STARTED
                    || action == BusinessAuditAction.COLLATERAL_LOAN_VERIFICATION_COMPLETED
                    || action == BusinessAuditAction.LOAN_APPLICATION_CANCELLED
                    || action == BusinessAuditAction.REPAYMENT_RECORDED
                    || action == BusinessAuditAction.LOAN_ACCOUNT_STATUS_CHANGED
                    || action == BusinessAuditAction.LOAN_SETTLEMENT_APPROVED
                    || action == BusinessAuditAction.LOAN_ACCOUNT_CLOSED) {
                continue;
            }
            assertTrue(sql.contains("'" + action.name() + "'"));
        }
        assertFalse(sql.contains(
                "'LOAN_CONTRACT_DISBURSEMENT_DESTINATION_REVEALED'"));
        assertFalse(sql.contains("'COLLATERAL_LOAN_APPLICATION_SUBMITTED'"));
        assertFalse(sql.contains("CREATE TABLE"));
        assertFalse(sql.contains("CREATE INDEX"));
        assertFalse(sql.contains("audit_events_entity_type"));
        assertFalse(sql.contains("loan_application_status_transitions"));
        assertFalse(sql.contains("loan_accounts"));
    }

    @Test
    void preservesCommittedV28ByteContentAcrossPlatforms() throws Exception {
        String normalized = Files.readString(MIGRATION_DIRECTORY.resolve(
                "V28__create_manual_disbursement_and_loan_account_activation_foundation.sql"
        )).replace("\r\n", "\n");
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                normalized.getBytes(StandardCharsets.UTF_8)
        );

        assertEquals(V28_NORMALIZED_SHA256, HexFormat.of().withUpperCase().formatHex(digest));
    }

    @Test
    void currentSchemaSnapshotDeclaresV44AndCurrentFoundations() throws Exception {
        String snapshot = Files.readString(Path.of(
                "../docs/database/MER-DB-CURRENT-SCHEMA.sql"
        ));

        assertTrue(snapshot.contains("Snapshot source: migrations V1 through V47"));
        assertTrue(snapshot.contains("CREATE TABLE unsecured_consumer_loan_verifications"));
        assertTrue(snapshot.contains("ADD COLUMN reviewed_by_user_id UUID"));
        assertTrue(snapshot.contains("'UNSECURED_CONSUMER_LOAN_VERIFICATION_STARTED'"));
        assertTrue(snapshot.contains("'UNSECURED_CONSUMER_LOAN_VERIFICATION_COMPLETED'"));
        assertTrue(snapshot.contains("CREATE TABLE loan_application_cancellations"));
        assertTrue(snapshot.contains("-- V29 manual disbursement audit action"));
        assertTrue(snapshot.contains("'MANUAL_DISBURSEMENT_CONFIRMED'"));
        assertTrue(snapshot.contains("'REPAYMENT_RECORDED'"));
        assertTrue(snapshot.contains("'LOAN_ACCOUNT_STATUS_CHANGED'"));
        assertTrue(snapshot.contains("CREATE TABLE repayment_transactions"));
        assertTrue(snapshot.contains("CREATE TABLE repayment_installment_progress"));
        assertTrue(snapshot.contains("CREATE TABLE approved_loan_settlements"));
        assertTrue(snapshot.contains("CREATE TABLE loan_account_closures"));
        assertTrue(snapshot.contains(
                "'LOAN_CONTRACT_DISBURSEMENT_DESTINATION_REVEALED'"));
        assertTrue(snapshot.contains("uq_loan_products_identity_tuple"));
        assertTrue(snapshot.contains("trg_loan_applications_product_identity_immutable"));
        assertTrue(snapshot.contains("'ON_SALARY_DATE', 'MONTHLY_INSTALLMENT'"));
    }
}
