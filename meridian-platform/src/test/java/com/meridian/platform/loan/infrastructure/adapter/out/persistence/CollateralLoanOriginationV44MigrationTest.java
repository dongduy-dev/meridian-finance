package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollateralLoanOriginationV44MigrationTest {

    private static final Path V44 = Path.of(
            "src/main/resources/db/migration/V44__add_collateral_origination_and_evidence_foundation.sql"
    );
    private static final Path V38 = Path.of(
            "src/main/resources/db/migration/V38__add_ucl_origination_and_evidence_foundation.sql"
    );
    private static final Path CURRENT_SCHEMA = Path.of(
            "../docs/database/MER-DB-CURRENT-SCHEMA.sql"
    );

    @Test
    void addsOnlyCollateralOriginationAndEvidenceFoundation() throws IOException {
        String sql = Files.readString(V44);
        String collateralTable = sql.substring(
                sql.indexOf("CREATE TABLE collaterals"),
                sql.indexOf("CREATE INDEX idx_collaterals_loan_application_id")
        );

        assertTrue(sql.contains("CREATE TABLE collaterals"));
        assertTrue(sql.contains("CREATE TABLE collateral_loan_verifications"));
        assertTrue(sql.contains("COLLATERAL_OWNERSHIP_EVIDENCE"));
        assertTrue(sql.contains("COLLATERAL_LOAN_APPLICATION_SUBMITTED"));
        assertTrue(sql.contains("product_verification_result = 'PENDING_MANUAL_REVIEW'"));
        assertFalse(collateralTable.contains("UNIQUE (loan_application_id)"));
        assertFalse(sql.contains("ownership_document_id"));
        assertFalse(sql.contains("loan_to_value"));
        assertFalse(sql.contains("interest_rate"));
        assertFalse(sql.contains("CREATE TABLE approved_offers"));
        assertFalse(sql.contains("CREATE TABLE loan_contracts"));
        assertFalse(sql.contains("CREATE TABLE loan_accounts"));
        assertFalse(sql.contains("CREATE TABLE repayment_schedules"));
    }

    @Test
    void preservesHistoricalUclMigrationVocabulary() throws IOException {
        String historical = Files.readString(V38);

        assertFalse(historical.contains("COLLATERAL_OWNERSHIP_EVIDENCE"));
        assertFalse(historical.contains("COLLATERAL_LOAN_APPLICATION_SUBMITTED"));
        assertFalse(historical.contains("CREATE TABLE collaterals"));
    }

    @Test
    void currentSchemaSnapshotDeclaresV44WithoutCrossModuleDocumentForeignKey() throws IOException {
        String snapshot = Files.readString(CURRENT_SCHEMA);

        assertTrue(snapshot.contains("Snapshot source: migrations V1 through V44"));
        assertTrue(snapshot.contains("CREATE TABLE collaterals"));
        assertTrue(snapshot.contains("CREATE TABLE collateral_loan_verifications"));
        assertTrue(snapshot.contains("'COLLATERAL_OWNERSHIP_EVIDENCE'"));
        assertTrue(snapshot.contains("'COLLATERAL_LOAN_APPLICATION_SUBMITTED'"));
        assertFalse(snapshot.contains("ownership_document_id"));
    }
}
