ALTER TABLE loan_applications
    ADD COLUMN origination_channel VARCHAR(30) NOT NULL DEFAULT 'CUSTOMER_DIGITAL';

ALTER TABLE loan_applications
    ADD CONSTRAINT chk_loan_applications_origination_channel
        CHECK (origination_channel IN ('CUSTOMER_DIGITAL', 'STAFF_ASSISTED')),
    ADD CONSTRAINT chk_loan_applications_product_channel
        CHECK (product_code <> 'SALARY_ADVANCE' OR origination_channel = 'CUSTOMER_DIGITAL');

CREATE OR REPLACE FUNCTION reject_loan_application_origination_channel_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.origination_channel IS DISTINCT FROM OLD.origination_channel THEN
        RAISE EXCEPTION 'Loan Application origination channel is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_loan_applications_origination_channel_immutable
BEFORE UPDATE OF origination_channel ON loan_applications
FOR EACH ROW
EXECUTE FUNCTION reject_loan_application_origination_channel_mutation();

ALTER TABLE assisted_origination_cases
    DROP CONSTRAINT chk_assisted_origination_cases_terminal,
    ADD COLUMN loan_application_id UUID,
    ADD CONSTRAINT fk_assisted_origination_cases_loan_application
        FOREIGN KEY (loan_application_id) REFERENCES loan_applications (id),
    ADD CONSTRAINT uq_assisted_origination_cases_loan_application
        UNIQUE (loan_application_id),
    ADD CONSTRAINT chk_assisted_origination_cases_terminal CHECK (
        (status = 'OPEN' AND terminal_at IS NULL AND loan_application_id IS NULL)
        OR (status = 'ABANDONED' AND terminal_at IS NOT NULL AND loan_application_id IS NULL)
        OR (status = 'COMPLETED' AND customer_id IS NOT NULL
            AND terminal_at IS NOT NULL AND loan_application_id IS NOT NULL)
    );

INSERT INTO permissions (id, code, description)
VALUES (
    '00000000-0000-0000-0000-000000000249',
    'document:upload:assisted',
    'Upload initial application evidence for Staff-assisted origination'
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.code = 'document:upload:assisted'
WHERE role.code = 'LOAN_OFFICER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

ALTER TABLE audit_events
    DROP CONSTRAINT chk_audit_events_action,
    ADD CONSTRAINT chk_audit_events_action CHECK (action IN (
        'CUSTOMER_PROFILE_CREATED', 'CUSTOMER_PROFILE_UPDATED',
        'CUSTOMER_PROFILE_COMPLETED', 'CUSTOMER_BANK_ACCOUNT_ADDED',
        'CUSTOMER_BANK_ACCOUNT_MADE_PRIMARY', 'CUSTOMER_BANK_ACCOUNT_DEACTIVATED',
        'SALARY_ADVANCE_APPLICATION_SUBMITTED',
        'UNSECURED_CONSUMER_LOAN_APPLICATION_SUBMITTED',
        'COLLATERAL_LOAN_APPLICATION_SUBMITTED',
        'COLLATERAL_LOAN_VERIFICATION_STARTED', 'COLLATERAL_LOAN_VERIFICATION_COMPLETED',
        'UNSECURED_CONSUMER_LOAN_VERIFICATION_STARTED',
        'UNSECURED_CONSUMER_LOAN_VERIFICATION_COMPLETED',
        'SALARY_ADVANCE_LIMIT_INITIALIZED', 'SALARY_ADVANCE_LIMIT_REFRESHED',
        'SALARY_ADVANCE_LIMIT_RESERVED', 'LOAN_REVIEW_STARTED',
        'REVIEW_RECOMMENDATION_RECORDED', 'APPROVAL_DECISION_RECORDED',
        'APPROVED_OFFER_GENERATED', 'APPROVED_OFFER_ACCEPTED',
        'APPROVED_OFFER_DECLINED', 'OFFER_EXPIRED', 'RESERVATION_RELEASED',
        'DOCUMENT_CHECKLIST_CREATED', 'DOCUMENT_VERSION_UPLOADED',
        'DOCUMENT_REVIEW_ACCEPTED', 'DOCUMENT_WAIVED',
        'DOCUMENT_REPLACEMENT_REQUESTED', 'DOCUMENT_UPLOADS_COMPLETED',
        'DOCUMENT_CHECKLIST_ITEM_CREATED', 'REVIEW_CYCLE_CREATED',
        'REVIEW_CYCLE_STATE_CHANGED', 'CORRECTION_REQUEST_CREATED',
        'CORRECTION_TASK_COMPLETED', 'CORRECTION_RESUBMITTED',
        'LOAN_APPLICATION_CANCELLED', 'SALARY_ADVANCE_REVALIDATED',
        'LOAN_CONTRACT_PREPARED', 'LOAN_CONTRACT_SUPERSEDED',
        'LOAN_CONTRACT_ACKNOWLEDGED', 'LOAN_CONTRACT_READINESS_CONFIRMED',
        'MANUAL_DISBURSEMENT_CONFIRMED',
        'LOAN_CONTRACT_DISBURSEMENT_DESTINATION_REVEALED',
        'REPAYMENT_RECORDED', 'LOAN_ACCOUNT_STATUS_CHANGED',
        'LOAN_SETTLEMENT_APPROVED', 'LOAN_ACCOUNT_CLOSED',
        'PARTNER_COMPANY_CREATED', 'PARTNER_COMPANY_UPDATED',
        'PARTNER_COMPANY_STATUS_CHANGED', 'PARTNER_EMPLOYEE_IMPORT_COMPLETED',
        'LOAN_PRODUCT_LIMITS_UPDATED', 'LOAN_PRODUCT_ACTIVATED',
        'LOAN_PRODUCT_DEACTIVATED', 'IDENTITY_USER_STATUS_CHANGED',
        'IDENTITY_USER_ROLE_ASSIGNED', 'IDENTITY_USER_ROLE_REMOVED',
        'STAFF_ASSISTED_CUSTOMER_CREATED', 'ASSISTED_ORIGINATION_CASE_CREATED',
        'ASSISTED_ORIGINATION_CUSTOMER_ASSOCIATED',
        'ASSISTED_ORIGINATION_CASE_ABANDONED',
        'ASSISTED_ORIGINATION_CASE_COMPLETED',
        'INTAKE_DOCUMENT_VERSION_UPLOADED'
    ));
