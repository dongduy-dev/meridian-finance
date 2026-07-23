DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM loan_applications WHERE status IN ('DISBURSEMENT_PENDING', 'DISBURSED')) THEN
        RAISE EXCEPTION 'Contract foundation requires explicit reconciliation of existing post-contract applications.';
    END IF;
END $$;

ALTER TABLE loan_applications
    ADD CONSTRAINT uq_loan_applications_id_customer UNIQUE (id, customer_id);

ALTER TABLE approved_offers
    ADD CONSTRAINT uq_approved_offers_id_application UNIQUE (id, loan_application_id);

ALTER TABLE customer_bank_accounts
    ADD CONSTRAINT uq_customer_bank_accounts_id_customer UNIQUE (id, customer_id);

CREATE TABLE loan_contracts (
    id UUID PRIMARY KEY,
    loan_application_id UUID NOT NULL,
    approved_offer_id UUID NOT NULL,
    contract_reference VARCHAR(80) NOT NULL,
    contract_version INTEGER NOT NULL,
    status VARCHAR(40) NOT NULL,

    approved_principal NUMERIC(19,2) NOT NULL,
    approved_term_months INTEGER NOT NULL,
    interest_calculation_method VARCHAR(50) NOT NULL,
    flat_monthly_interest_rate NUMERIC(9,6) NOT NULL,
    total_interest NUMERIC(19,2) NOT NULL,
    fee_amount NUMERIC(19,2) NOT NULL,
    total_repayment_amount NUMERIC(19,2) NOT NULL,
    repayment_method VARCHAR(50) NOT NULL,

    customer_id UUID NOT NULL,
    source_bank_account_id UUID NOT NULL,
    bank_code VARCHAR(50) NOT NULL,
    bank_name_snapshot VARCHAR(150) NOT NULL,
    account_holder_name VARCHAR(200) NOT NULL,
    account_number_last_four VARCHAR(4) NOT NULL,
    primary_at_capture BOOLEAN NOT NULL,
    active_at_capture BOOLEAN NOT NULL,
    account_captured_at TIMESTAMP NOT NULL,
    protection_scheme VARCHAR(40) NOT NULL,
    protection_key_id VARCHAR(80) NOT NULL,
    protection_nonce BYTEA NOT NULL,
    protected_account_number BYTEA NOT NULL,
    protection_aad_version VARCHAR(80) NOT NULL,

    preparation_request_id UUID NOT NULL,
    expected_previous_contract_version INTEGER,
    supersession_reason VARCHAR(80),
    prepared_by_user_id UUID NOT NULL,
    prepared_at TIMESTAMP NOT NULL,

    acknowledgment_request_id UUID,
    acknowledged_by_user_id UUID,
    acknowledged_at TIMESTAMP,
    confirmation_request_id UUID,
    confirmed_by_user_id UUID,
    confirmed_at TIMESTAMP,

    supersedes_contract_id UUID,
    superseded_by_user_id UUID,
    superseded_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_loan_contracts_application FOREIGN KEY (loan_application_id)
        REFERENCES loan_applications (id),
    CONSTRAINT fk_loan_contracts_application_customer FOREIGN KEY (loan_application_id, customer_id)
        REFERENCES loan_applications (id, customer_id),
    CONSTRAINT fk_loan_contracts_offer_application FOREIGN KEY (approved_offer_id, loan_application_id)
        REFERENCES approved_offers (id, loan_application_id),
    CONSTRAINT fk_loan_contracts_source_account_customer FOREIGN KEY (source_bank_account_id, customer_id)
        REFERENCES customer_bank_accounts (id, customer_id),
    CONSTRAINT fk_loan_contracts_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_loan_contracts_supersedes FOREIGN KEY (supersedes_contract_id) REFERENCES loan_contracts (id),
    CONSTRAINT fk_loan_contracts_prepared_by FOREIGN KEY (prepared_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_loan_contracts_acknowledged_by FOREIGN KEY (acknowledged_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_loan_contracts_confirmed_by FOREIGN KEY (confirmed_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_loan_contracts_superseded_by FOREIGN KEY (superseded_by_user_id) REFERENCES users (id),

    CONSTRAINT uq_loan_contracts_reference UNIQUE (contract_reference),
    CONSTRAINT uq_loan_contracts_application_version UNIQUE (loan_application_id, contract_version),
    CONSTRAINT uq_loan_contracts_preparation_request UNIQUE (preparation_request_id),
    CONSTRAINT uq_loan_contracts_acknowledgment_request UNIQUE (acknowledgment_request_id),
    CONSTRAINT uq_loan_contracts_confirmation_request UNIQUE (confirmation_request_id),

    CONSTRAINT chk_loan_contracts_version_positive CHECK (contract_version > 0),
    CONSTRAINT chk_loan_contracts_status CHECK (status IN (
        'PREPARED', 'ACKNOWLEDGED', 'READY_FOR_DISBURSEMENT', 'SUPERSEDED'
    )),
    CONSTRAINT chk_loan_contracts_terms CHECK (
        approved_principal > 0 AND approved_term_months > 0
        AND flat_monthly_interest_rate >= 0 AND total_interest >= 0 AND fee_amount >= 0
        AND total_repayment_amount = approved_principal + total_interest + fee_amount
        AND approved_principal = trunc(approved_principal)
        AND total_interest = trunc(total_interest)
        AND fee_amount = trunc(fee_amount)
        AND total_repayment_amount = trunc(total_repayment_amount)
        AND interest_calculation_method = 'FLAT_ORIGINAL_PRINCIPAL'
        AND repayment_method = 'ON_SALARY_DATE'
    ),
    CONSTRAINT chk_loan_contracts_safe_account_snapshot CHECK (
        btrim(bank_code) <> '' AND btrim(bank_name_snapshot) <> ''
        AND btrim(account_holder_name) <> ''
        AND length(btrim(account_number_last_four)) BETWEEN 1 AND 4
        AND primary_at_capture AND active_at_capture
        AND btrim(protection_scheme) <> '' AND btrim(protection_key_id) <> ''
        AND octet_length(protection_nonce) > 0 AND octet_length(protected_account_number) > 0
        AND btrim(protection_aad_version) <> ''
    ),
    CONSTRAINT chk_loan_contracts_version_origin CHECK (
        (contract_version = 1 AND expected_previous_contract_version IS NULL
            AND supersession_reason IS NULL AND supersedes_contract_id IS NULL)
        OR (contract_version > 1 AND expected_previous_contract_version = contract_version - 1
            AND supersession_reason = 'DISBURSEMENT_ACCOUNT_REFRESH' AND supersedes_contract_id IS NOT NULL)
    ),
    CONSTRAINT chk_loan_contracts_lifecycle CHECK (
        (status = 'PREPARED'
            AND acknowledgment_request_id IS NULL AND acknowledged_by_user_id IS NULL AND acknowledged_at IS NULL
            AND confirmation_request_id IS NULL AND confirmed_by_user_id IS NULL AND confirmed_at IS NULL
            AND superseded_by_user_id IS NULL AND superseded_at IS NULL)
        OR (status = 'ACKNOWLEDGED'
            AND acknowledgment_request_id IS NOT NULL AND acknowledged_by_user_id IS NOT NULL AND acknowledged_at IS NOT NULL
            AND confirmation_request_id IS NULL AND confirmed_by_user_id IS NULL AND confirmed_at IS NULL
            AND superseded_by_user_id IS NULL AND superseded_at IS NULL)
        OR (status = 'READY_FOR_DISBURSEMENT'
            AND acknowledgment_request_id IS NOT NULL AND acknowledged_by_user_id IS NOT NULL AND acknowledged_at IS NOT NULL
            AND confirmation_request_id IS NOT NULL AND confirmed_by_user_id IS NOT NULL AND confirmed_at IS NOT NULL
            AND superseded_by_user_id IS NULL AND superseded_at IS NULL)
        OR (status = 'SUPERSEDED'
            AND confirmation_request_id IS NULL AND confirmed_by_user_id IS NULL AND confirmed_at IS NULL
            AND superseded_by_user_id IS NOT NULL AND superseded_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_loan_contracts_current_application
    ON loan_contracts (loan_application_id) WHERE status <> 'SUPERSEDED';
CREATE INDEX idx_loan_contracts_application_version_desc
    ON loan_contracts (loan_application_id, contract_version DESC);
CREATE INDEX idx_loan_contracts_source_account
    ON loan_contracts (source_bank_account_id);

CREATE TABLE loan_contract_repayment_items (
    id UUID PRIMARY KEY,
    loan_contract_id UUID NOT NULL,
    source_approved_offer_repayment_item_id UUID NOT NULL,
    installment_number INTEGER NOT NULL,
    principal_due NUMERIC(19,2) NOT NULL,
    interest_due NUMERIC(19,2) NOT NULL,
    fee_due NUMERIC(19,2) NOT NULL,
    total_due NUMERIC(19,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_contract_repayment_items_contract FOREIGN KEY (loan_contract_id)
        REFERENCES loan_contracts (id),
    CONSTRAINT fk_loan_contract_repayment_items_offer_item FOREIGN KEY (source_approved_offer_repayment_item_id)
        REFERENCES approved_offer_repayment_items (id),
    CONSTRAINT uq_loan_contract_repayment_items_installment UNIQUE (loan_contract_id, installment_number),
    CONSTRAINT uq_loan_contract_repayment_items_source UNIQUE (loan_contract_id, source_approved_offer_repayment_item_id),
    CONSTRAINT chk_loan_contract_repayment_items_valid CHECK (
        installment_number > 0 AND principal_due >= 0 AND interest_due >= 0
        AND fee_due >= 0 AND total_due = principal_due + interest_due + fee_due
        AND principal_due = trunc(principal_due) AND interest_due = trunc(interest_due)
        AND fee_due = trunc(fee_due) AND total_due = trunc(total_due)
    )
);
CREATE INDEX idx_loan_contract_repayment_items_contract ON loan_contract_repayment_items (loan_contract_id);

CREATE OR REPLACE FUNCTION enforce_loan_contract_immutability()
RETURNS trigger AS $$
BEGIN
    IF ROW(
        NEW.id, NEW.loan_application_id, NEW.approved_offer_id, NEW.contract_reference, NEW.contract_version,
        NEW.approved_principal, NEW.approved_term_months, NEW.interest_calculation_method,
        NEW.flat_monthly_interest_rate, NEW.total_interest, NEW.fee_amount, NEW.total_repayment_amount,
        NEW.repayment_method, NEW.customer_id, NEW.source_bank_account_id, NEW.bank_code,
        NEW.bank_name_snapshot, NEW.account_holder_name, NEW.account_number_last_four,
        NEW.primary_at_capture, NEW.active_at_capture, NEW.account_captured_at, NEW.protection_scheme,
        NEW.protection_key_id, NEW.protection_nonce, NEW.protected_account_number, NEW.protection_aad_version,
        NEW.preparation_request_id, NEW.expected_previous_contract_version, NEW.supersession_reason,
        NEW.prepared_by_user_id, NEW.prepared_at, NEW.supersedes_contract_id, NEW.created_at
    ) IS DISTINCT FROM ROW(
        OLD.id, OLD.loan_application_id, OLD.approved_offer_id, OLD.contract_reference, OLD.contract_version,
        OLD.approved_principal, OLD.approved_term_months, OLD.interest_calculation_method,
        OLD.flat_monthly_interest_rate, OLD.total_interest, OLD.fee_amount, OLD.total_repayment_amount,
        OLD.repayment_method, OLD.customer_id, OLD.source_bank_account_id, OLD.bank_code,
        OLD.bank_name_snapshot, OLD.account_holder_name, OLD.account_number_last_four,
        OLD.primary_at_capture, OLD.active_at_capture, OLD.account_captured_at, OLD.protection_scheme,
        OLD.protection_key_id, OLD.protection_nonce, OLD.protected_account_number, OLD.protection_aad_version,
        OLD.preparation_request_id, OLD.expected_previous_contract_version, OLD.supersession_reason,
        OLD.prepared_by_user_id, OLD.prepared_at, OLD.supersedes_contract_id, OLD.created_at
    ) THEN
        RAISE EXCEPTION 'Immutable loan contract snapshot fields cannot be changed';
    END IF;
    IF OLD.status = 'ACKNOWLEDGED' AND ROW(
        NEW.acknowledgment_request_id, NEW.acknowledged_by_user_id, NEW.acknowledged_at
    ) IS DISTINCT FROM ROW(
        OLD.acknowledgment_request_id, OLD.acknowledged_by_user_id, OLD.acknowledged_at
    ) THEN
        RAISE EXCEPTION 'Contract acknowledgment evidence is immutable';
    END IF;
    IF NOT (
        (OLD.status = 'PREPARED' AND NEW.status IN ('ACKNOWLEDGED', 'SUPERSEDED'))
        OR (OLD.status = 'ACKNOWLEDGED' AND NEW.status IN ('READY_FOR_DISBURSEMENT', 'SUPERSEDED'))
    ) THEN
        RAISE EXCEPTION 'Illegal loan contract lifecycle transition';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_loan_contracts_immutable
    BEFORE UPDATE ON loan_contracts FOR EACH ROW EXECUTE FUNCTION enforce_loan_contract_immutability();
CREATE TRIGGER trg_loan_contracts_no_delete
    BEFORE DELETE ON loan_contracts FOR EACH ROW EXECUTE FUNCTION reject_immutable_history_row_mutation();
CREATE TRIGGER trg_loan_contract_repayment_items_immutable
    BEFORE UPDATE OR DELETE ON loan_contract_repayment_items
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_history_row_mutation();

CREATE OR REPLACE FUNCTION validate_loan_contract_repayment_reconciliation()
RETURNS trigger AS $$
DECLARE
    target_contract_id UUID;
    contract_row loan_contracts%ROWTYPE;
    item_count BIGINT;
    principal_sum NUMERIC(19,2);
    interest_sum NUMERIC(19,2);
    fee_sum NUMERIC(19,2);
    total_sum NUMERIC(19,2);
BEGIN
    IF TG_TABLE_NAME = 'loan_contracts' THEN
        target_contract_id := COALESCE(NEW.id, OLD.id);
    ELSE
        target_contract_id := COALESCE(NEW.loan_contract_id, OLD.loan_contract_id);
    END IF;
    SELECT * INTO contract_row FROM loan_contracts WHERE id = target_contract_id;
    IF NOT FOUND THEN RETURN NULL; END IF;
    SELECT COUNT(*), COALESCE(SUM(principal_due), 0), COALESCE(SUM(interest_due), 0),
           COALESCE(SUM(fee_due), 0), COALESCE(SUM(total_due), 0)
    INTO item_count, principal_sum, interest_sum, fee_sum, total_sum
    FROM loan_contract_repayment_items WHERE loan_contract_id = target_contract_id;
    IF item_count <> contract_row.approved_term_months
        OR principal_sum <> contract_row.approved_principal
        OR interest_sum <> contract_row.total_interest
        OR fee_sum <> contract_row.fee_amount
        OR total_sum <> contract_row.total_repayment_amount THEN
        RAISE EXCEPTION 'Loan contract repayment items do not reconcile';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_loan_contract_repayment_reconcile_contract
    AFTER INSERT OR UPDATE ON loan_contracts DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_loan_contract_repayment_reconciliation();
CREATE CONSTRAINT TRIGGER trg_loan_contract_repayment_reconcile_items
    AFTER INSERT OR UPDATE OR DELETE ON loan_contract_repayment_items DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_loan_contract_repayment_reconciliation();

ALTER TABLE loan_application_status_transitions
    DROP CONSTRAINT chk_loan_application_status_transitions_action;
ALTER TABLE loan_application_status_transitions
    ADD CONSTRAINT chk_loan_application_status_transitions_action CHECK (action IN (
        'SUBMIT_APPLICATION', 'COMPLETE_DOCUMENT_UPLOADS', 'START_REVIEW',
        'RECOMMEND_APPROVAL', 'RECOMMEND_REJECTION', 'RETURN_TO_CUSTOMER_REVISION',
        'REQUEST_STAFF_CORRECTION', 'APPROVE', 'REJECT', 'RETURN_TO_LOAN_OFFICER_REVIEW',
        'REQUEST_CUSTOMER_OR_STAFF_CORRECTION', 'RESUBMIT_CORRECTION', 'GENERATE_APPROVED_OFFER',
        'ACCEPT_APPROVED_OFFER', 'DECLINE_APPROVED_OFFER', 'EXPIRE_APPROVED_OFFER',
        'CONFIRM_DISBURSEMENT_READINESS'
    ));

ALTER TABLE audit_events DROP CONSTRAINT chk_audit_events_entity_type, DROP CONSTRAINT chk_audit_events_action;
ALTER TABLE audit_events
    ADD CONSTRAINT chk_audit_events_entity_type CHECK (entity_type IN (
        'CUSTOMER', 'CUSTOMER_BANK_ACCOUNT', 'LOAN_APPLICATION', 'SALARY_ADVANCE_LIMIT_MOVEMENT',
        'REVIEW_RECOMMENDATION', 'APPROVAL_DECISION', 'APPROVED_OFFER', 'DOCUMENT_CHECKLIST',
        'DOCUMENT_CHECKLIST_ITEM', 'DOCUMENT_VERSION', 'DOCUMENT_REVIEW_DECISION', 'LOAN_REVIEW_CYCLE',
        'LOAN_CORRECTION_REQUEST', 'LOAN_CORRECTION_TASK', 'SALARY_ADVANCE_VERIFICATION', 'LOAN_CONTRACT'
    )),
    ADD CONSTRAINT chk_audit_events_action CHECK (action IN (
        'CUSTOMER_PROFILE_CREATED', 'CUSTOMER_PROFILE_UPDATED', 'CUSTOMER_PROFILE_COMPLETED',
        'CUSTOMER_BANK_ACCOUNT_ADDED', 'CUSTOMER_BANK_ACCOUNT_MADE_PRIMARY',
        'CUSTOMER_BANK_ACCOUNT_DEACTIVATED', 'SALARY_ADVANCE_APPLICATION_SUBMITTED',
        'SALARY_ADVANCE_LIMIT_INITIALIZED', 'SALARY_ADVANCE_LIMIT_REFRESHED',
        'SALARY_ADVANCE_LIMIT_RESERVED', 'LOAN_REVIEW_STARTED', 'REVIEW_RECOMMENDATION_RECORDED',
        'APPROVAL_DECISION_RECORDED', 'APPROVED_OFFER_GENERATED', 'APPROVED_OFFER_ACCEPTED',
        'APPROVED_OFFER_DECLINED', 'OFFER_EXPIRED', 'RESERVATION_RELEASED',
        'DOCUMENT_CHECKLIST_CREATED', 'DOCUMENT_CHECKLIST_ITEM_CREATED', 'DOCUMENT_VERSION_UPLOADED',
        'DOCUMENT_REVIEW_ACCEPTED', 'DOCUMENT_WAIVED', 'DOCUMENT_REPLACEMENT_REQUESTED',
        'DOCUMENT_UPLOADS_COMPLETED', 'REVIEW_CYCLE_CREATED', 'REVIEW_CYCLE_STATE_CHANGED',
        'CORRECTION_REQUEST_CREATED', 'CORRECTION_TASK_COMPLETED', 'CORRECTION_RESUBMITTED',
        'SALARY_ADVANCE_REVALIDATED', 'LOAN_CONTRACT_PREPARED', 'LOAN_CONTRACT_SUPERSEDED',
        'LOAN_CONTRACT_ACKNOWLEDGED', 'LOAN_CONTRACT_READINESS_CONFIRMED'
    ));
ALTER TABLE loan_contracts
    DROP CONSTRAINT fk_loan_contracts_supersedes,
    ADD CONSTRAINT uq_loan_contracts_id_application_version
        UNIQUE (id, loan_application_id, contract_version),
    ADD CONSTRAINT fk_loan_contracts_supersedes_version
        FOREIGN KEY (supersedes_contract_id, loan_application_id, expected_previous_contract_version)
        REFERENCES loan_contracts (id, loan_application_id, contract_version);

CREATE OR REPLACE FUNCTION validate_loan_contract_source_snapshot()
RETURNS trigger AS $$
DECLARE
    target_contract_id UUID;
    contract_row loan_contracts%ROWTYPE;
BEGIN
    IF TG_TABLE_NAME = 'loan_contracts' THEN
        target_contract_id := COALESCE(NEW.id, OLD.id);
    ELSE
        target_contract_id := COALESCE(NEW.loan_contract_id, OLD.loan_contract_id);
    END IF;
    SELECT * INTO contract_row FROM loan_contracts WHERE id = target_contract_id;
    IF NOT FOUND THEN RETURN NULL; END IF;
    IF NOT EXISTS (
        SELECT 1 FROM approved_offers offer
        WHERE offer.id = contract_row.approved_offer_id
          AND offer.loan_application_id = contract_row.loan_application_id
          AND offer.status = 'ACCEPTED'
          AND offer.approved_principal = contract_row.approved_principal
          AND offer.approved_term_months = contract_row.approved_term_months
          AND offer.interest_calculation_method = contract_row.interest_calculation_method
          AND offer.flat_monthly_interest_rate = contract_row.flat_monthly_interest_rate
          AND offer.total_interest = contract_row.total_interest
          AND offer.fee_amount = contract_row.fee_amount
          AND offer.total_repayment_amount = contract_row.total_repayment_amount
          AND offer.repayment_method = contract_row.repayment_method
    ) THEN
        RAISE EXCEPTION 'Loan contract terms do not match the accepted approved offer';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM loan_contract_repayment_items item
        LEFT JOIN approved_offer_repayment_items source ON source.id = item.source_approved_offer_repayment_item_id
        WHERE item.loan_contract_id = target_contract_id
          AND (source.id IS NULL OR source.approved_offer_id <> contract_row.approved_offer_id
            OR source.installment_number <> item.installment_number
            OR source.principal_due <> item.principal_due OR source.interest_due <> item.interest_due
            OR source.fee_due <> item.fee_due OR source.total_due <> item.total_due)
    ) THEN
        RAISE EXCEPTION 'Loan contract repayment snapshot does not match the accepted approved offer';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_loan_contract_source_contract
    AFTER INSERT OR UPDATE ON loan_contracts DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_loan_contract_source_snapshot();
CREATE CONSTRAINT TRIGGER trg_loan_contract_source_items
    AFTER INSERT OR UPDATE OR DELETE ON loan_contract_repayment_items DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_loan_contract_source_snapshot();

CREATE OR REPLACE FUNCTION validate_loan_contract_application_lifecycle()
RETURNS trigger AS $$
DECLARE
    target_application_id UUID;
    application_status VARCHAR(50);
    ready_contract_count BIGINT;
BEGIN
    IF TG_TABLE_NAME = 'loan_applications' THEN
        target_application_id := COALESCE(NEW.id, OLD.id);
    ELSE
        target_application_id := COALESCE(NEW.loan_application_id, OLD.loan_application_id);
    END IF;
    SELECT status INTO application_status FROM loan_applications WHERE id = target_application_id;
    IF NOT FOUND THEN RETURN NULL; END IF;
    SELECT COUNT(*) INTO ready_contract_count FROM loan_contracts
    WHERE loan_application_id = target_application_id AND status = 'READY_FOR_DISBURSEMENT';
    IF application_status = 'DISBURSEMENT_PENDING' AND ready_contract_count <> 1 THEN
        RAISE EXCEPTION 'Disbursement-pending application requires one ready current contract';
    END IF;
    IF ready_contract_count > 0 AND application_status <> 'DISBURSEMENT_PENDING' THEN
        RAISE EXCEPTION 'Ready contract requires disbursement-pending application';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_loan_contract_application_consistency_contract
    AFTER INSERT OR UPDATE ON loan_contracts DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_loan_contract_application_lifecycle();
CREATE CONSTRAINT TRIGGER trg_loan_contract_application_consistency_application
    AFTER INSERT OR UPDATE ON loan_applications DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_loan_contract_application_lifecycle();
