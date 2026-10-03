-- Customer owns manual verification outcomes; Document owns exact evidence versions.
-- No authoritative Customer verification workflow predates this migration. Do not fabricate history.
UPDATE customers SET verification_status = 'UNVERIFIED' WHERE verification_status = 'VERIFIED';

CREATE TABLE customer_identity_documents (
    id UUID PRIMARY KEY, customer_id UUID NOT NULL UNIQUE REFERENCES customers(id),
    current_version_id UUID, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL
);
CREATE TABLE customer_identity_document_versions (
    id UUID PRIMARY KEY, document_id UUID NOT NULL REFERENCES customer_identity_documents(id),
    version_number INTEGER NOT NULL CHECK (version_number > 0), upload_request_id UUID NOT NULL UNIQUE,
    baseline_version_id UUID, original_filename VARCHAR(255) NOT NULL,
    declared_mime_type VARCHAR(100) NOT NULL, detected_mime_type VARCHAR(100) NOT NULL,
    byte_size BIGINT NOT NULL CHECK (byte_size > 0 AND byte_size <= 10485760),
    sha256_hex VARCHAR(64) NOT NULL CHECK (sha256_hex ~ '^[0-9a-f]{64}$'),
    storage_key VARCHAR(500) NOT NULL UNIQUE, uploader_user_id UUID NOT NULL REFERENCES users(id),
    uploaded_at TIMESTAMP NOT NULL,
    UNIQUE (document_id, version_number), UNIQUE (document_id, id),
    CHECK (btrim(original_filename) <> '' AND original_filename !~ '[\\/\x00-\x1F\x7F]' AND position('..' in original_filename) = 0),
    CHECK (declared_mime_type = detected_mime_type AND detected_mime_type IN ('application/pdf', 'image/jpeg', 'image/png')),
    FOREIGN KEY (document_id, baseline_version_id) REFERENCES customer_identity_document_versions(document_id, id)
);
ALTER TABLE customer_identity_documents ADD CONSTRAINT fk_customer_identity_current_version
    FOREIGN KEY (id, current_version_id) REFERENCES customer_identity_document_versions(document_id, id);
CREATE TRIGGER trg_customer_identity_versions_immutable BEFORE UPDATE OR DELETE ON customer_identity_document_versions
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_history_row_mutation();

CREATE TABLE customer_identity_verifications (
    id UUID PRIMARY KEY, customer_id UUID NOT NULL REFERENCES customers(id),
    sequence_number INTEGER NOT NULL CHECK (sequence_number > 0),
    evidence_source VARCHAR(40) NOT NULL CHECK (evidence_source IN ('CUSTOMER_DIGITAL', 'STAFF_ASSISTED_INTAKE')),
    assisted_origination_case_id UUID REFERENCES assisted_origination_cases(id),
    document_version_id UUID NOT NULL,
    digital_version_id UUID GENERATED ALWAYS AS (CASE WHEN evidence_source = 'CUSTOMER_DIGITAL' THEN document_version_id END) STORED
        REFERENCES customer_identity_document_versions(id),
    intake_version_id UUID GENERATED ALWAYS AS (CASE WHEN evidence_source = 'STAFF_ASSISTED_INTAKE' THEN document_version_id END) STORED
        REFERENCES intake_document_versions(id),
    identity_full_name VARCHAR(255) NOT NULL CHECK (btrim(identity_full_name) <> ''),
    verification_method VARCHAR(40) NOT NULL DEFAULT 'MANUAL_STAFF_DOCUMENT_REVIEW' CHECK (verification_method = 'MANUAL_STAFF_DOCUMENT_REVIEW'),
    status VARCHAR(30) NOT NULL CHECK (status IN ('PENDING_REVIEW', 'VERIFIED', 'REJECTED', 'SUPERSEDED')),
    rejection_reason VARCHAR(40) CHECK (rejection_reason IN ('IDENTITY_REFERENCE_MISMATCH', 'NAME_MISMATCH', 'UNREADABLE_EVIDENCE', 'UNACCEPTABLE_EVIDENCE')),
    submitted_by UUID NOT NULL REFERENCES users(id), submitted_at TIMESTAMP NOT NULL,
    reviewed_by UUID REFERENCES users(id), completed_at TIMESTAMP, decision_request_id UUID UNIQUE,
    UNIQUE (customer_id, sequence_number), UNIQUE (customer_id, document_version_id),
    CHECK ((evidence_source = 'CUSTOMER_DIGITAL' AND assisted_origination_case_id IS NULL)
        OR (evidence_source = 'STAFF_ASSISTED_INTAKE' AND assisted_origination_case_id IS NOT NULL)),
    CHECK ((status = 'PENDING_REVIEW' AND reviewed_by IS NULL AND completed_at IS NULL AND decision_request_id IS NULL AND rejection_reason IS NULL)
        OR (status = 'SUPERSEDED' AND reviewed_by IS NULL AND completed_at IS NOT NULL AND decision_request_id IS NULL AND rejection_reason IS NULL)
        OR (status = 'VERIFIED' AND reviewed_by IS NOT NULL AND completed_at IS NOT NULL AND decision_request_id IS NOT NULL AND rejection_reason IS NULL)
        OR (status = 'REJECTED' AND reviewed_by IS NOT NULL AND completed_at IS NOT NULL AND decision_request_id IS NOT NULL AND rejection_reason IS NOT NULL)),
    CHECK (completed_at IS NULL OR completed_at >= submitted_at)
);
CREATE UNIQUE INDEX uq_customer_identity_pending ON customer_identity_verifications(customer_id) WHERE status = 'PENDING_REVIEW';
CREATE INDEX idx_customer_identity_pending_queue ON customer_identity_verifications(submitted_at, id) WHERE status = 'PENDING_REVIEW';

CREATE FUNCTION enforce_customer_identity_verification() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'Customer identity verification history is immutable'; END IF;
    IF TG_OP = 'UPDATE' THEN
        IF OLD.status <> 'PENDING_REVIEW' OR NEW.status = 'PENDING_REVIEW'
            OR ROW(NEW.id, NEW.customer_id, NEW.sequence_number, NEW.evidence_source, NEW.assisted_origination_case_id,
                NEW.document_version_id, NEW.identity_full_name, NEW.verification_method, NEW.submitted_by, NEW.submitted_at)
            IS DISTINCT FROM ROW(OLD.id, OLD.customer_id, OLD.sequence_number, OLD.evidence_source, OLD.assisted_origination_case_id,
                OLD.document_version_id, OLD.identity_full_name, OLD.verification_method, OLD.submitted_by, OLD.submitted_at) THEN
            RAISE EXCEPTION 'Customer identity verification history is immutable';
        END IF;
    ELSIF NEW.status <> 'PENDING_REVIEW' THEN
        RAISE EXCEPTION 'New identity verification must be pending';
    END IF;
    IF NEW.status <> 'SUPERSEDED' THEN
        IF NEW.evidence_source = 'CUSTOMER_DIGITAL' THEN
            IF NOT EXISTS (SELECT 1 FROM customer_identity_documents d
                WHERE d.customer_id = NEW.customer_id AND d.current_version_id = NEW.document_version_id) THEN
                RAISE EXCEPTION 'Customer identity evidence binding is stale or invalid';
            END IF;
        ELSE
            IF NOT EXISTS (SELECT 1 FROM intake_documents d JOIN assisted_origination_cases c ON c.id = d.assisted_origination_case_id
                WHERE c.id = NEW.assisted_origination_case_id AND c.customer_id = NEW.customer_id
                    AND d.evidence_type = 'CUSTOMER_IDENTITY' AND d.current_version_id = NEW.document_version_id) THEN
                RAISE EXCEPTION 'Intake identity evidence binding is stale or invalid';
            END IF;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM customer_profiles p JOIN customers c ON c.id = p.customer_id
            WHERE p.customer_id = NEW.customer_id AND p.full_name = NEW.identity_full_name
                AND c.status = 'ACTIVE' AND c.profile_completion_status = 'COMPLETE') THEN
            RAISE EXCEPTION 'Customer identity context is stale or incomplete';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_customer_identity_verification BEFORE INSERT OR UPDATE OR DELETE ON customer_identity_verifications
    FOR EACH ROW EXECUTE FUNCTION enforce_customer_identity_verification();

ALTER TABLE loan_applications ADD COLUMN identity_verification_id UUID REFERENCES customer_identity_verifications(id);
CREATE FUNCTION enforce_loan_identity_provenance() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND NEW.identity_verification_id IS DISTINCT FROM OLD.identity_verification_id THEN
        RAISE EXCEPTION 'Loan identity verification provenance is immutable';
    END IF;
    IF TG_OP = 'INSERT' AND NEW.identity_verification_id IS NOT NULL AND
        (NEW.product_code NOT IN ('SALARY_ADVANCE', 'UNSECURED_CONSUMER_LOAN', 'COLLATERAL_LOAN') OR NOT EXISTS (
            SELECT 1 FROM customer_identity_verifications v WHERE v.id = NEW.identity_verification_id
                AND v.customer_id = NEW.customer_id AND v.status = 'VERIFIED')) THEN
        RAISE EXCEPTION 'Loan identity verification provenance is invalid';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_loan_identity_provenance BEFORE INSERT OR UPDATE ON loan_applications
    FOR EACH ROW EXECUTE FUNCTION enforce_loan_identity_provenance();

INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'customer:identity:write:own', 'Submit own Customer-level identity evidence for manual verification'),
    (gen_random_uuid(), 'customer:identity:read:own', 'Read own Customer identity verification history and exact evidence'),
    (gen_random_uuid(), 'customer:identity:verify', 'Review exact Customer identity evidence and record a manual verification decision');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON
    (r.code = 'CUSTOMER' AND p.code IN ('customer:identity:write:own', 'customer:identity:read:own'))
    OR (r.code = 'LOAN_OFFICER' AND p.code = 'customer:identity:verify');

ALTER TABLE audit_events DROP CONSTRAINT chk_audit_events_action,
    ADD CONSTRAINT chk_audit_events_action CHECK (action IN (
        'CUSTOMER_IDENTITY_EVIDENCE_SUBMITTED',
        'CUSTOMER_IDENTITY_VERIFIED',
        'CUSTOMER_IDENTITY_REJECTED',
        'CUSTOMER_IDENTITY_VERIFICATION_INVALIDATED',
        'CUSTOMER_PROFILE_CREATED',
        'CUSTOMER_PROFILE_UPDATED',
        'CUSTOMER_PROFILE_COMPLETED',
        'CUSTOMER_BANK_ACCOUNT_ADDED',
        'CUSTOMER_BANK_ACCOUNT_MADE_PRIMARY',
        'CUSTOMER_BANK_ACCOUNT_DEACTIVATED',
        'SALARY_ADVANCE_APPLICATION_SUBMITTED',
        'UNSECURED_CONSUMER_LOAN_APPLICATION_SUBMITTED',
        'COLLATERAL_LOAN_APPLICATION_SUBMITTED',
        'COLLATERAL_LOAN_VERIFICATION_STARTED',
        'COLLATERAL_LOAN_VERIFICATION_COMPLETED',
        'UNSECURED_CONSUMER_LOAN_VERIFICATION_STARTED',
        'UNSECURED_CONSUMER_LOAN_VERIFICATION_COMPLETED',
        'SALARY_ADVANCE_LIMIT_INITIALIZED',
        'SALARY_ADVANCE_LIMIT_REFRESHED',
        'SALARY_ADVANCE_LIMIT_RESERVED',
        'LOAN_REVIEW_STARTED',
        'REVIEW_RECOMMENDATION_RECORDED',
        'APPROVAL_DECISION_RECORDED',
        'APPROVED_OFFER_GENERATED',
        'APPROVED_OFFER_ACCEPTED',
        'APPROVED_OFFER_DECLINED',
        'OFFER_EXPIRED',
        'RESERVATION_RELEASED',
        'DOCUMENT_CHECKLIST_CREATED',
        'DOCUMENT_VERSION_UPLOADED',
        'DOCUMENT_REVIEW_ACCEPTED',
        'DOCUMENT_WAIVED',
        'DOCUMENT_REPLACEMENT_REQUESTED',
        'DOCUMENT_UPLOADS_COMPLETED',
        'DOCUMENT_CHECKLIST_ITEM_CREATED',
        'REVIEW_CYCLE_CREATED',
        'REVIEW_CYCLE_STATE_CHANGED',
        'CORRECTION_REQUEST_CREATED',
        'CORRECTION_TASK_COMPLETED',
        'CORRECTION_RESUBMITTED',
        'LOAN_APPLICATION_CANCELLED',
        'SALARY_ADVANCE_REVALIDATED',
        'LOAN_CONTRACT_PREPARED',
        'LOAN_CONTRACT_SUPERSEDED',
        'LOAN_CONTRACT_ACKNOWLEDGED',
        'LOAN_CONTRACT_READINESS_CONFIRMED',
        'MANUAL_DISBURSEMENT_CONFIRMED',
        'LOAN_CONTRACT_DISBURSEMENT_DESTINATION_REVEALED',
        'REPAYMENT_RECORDED',
        'LOAN_ACCOUNT_STATUS_CHANGED',
        'LOAN_SETTLEMENT_APPROVED',
        'LOAN_ACCOUNT_CLOSED',
        'PARTNER_COMPANY_CREATED',
        'PARTNER_COMPANY_UPDATED',
        'PARTNER_COMPANY_STATUS_CHANGED',
        'PARTNER_EMPLOYEE_IMPORT_COMPLETED',
        'PARTNER_ELIGIBILITY_REVIEW_APPROVED',
        'PARTNER_ELIGIBILITY_REVIEW_REJECTED',
        'LOAN_PRODUCT_LIMITS_UPDATED',
        'LOAN_PRODUCT_ACTIVATED',
        'LOAN_PRODUCT_DEACTIVATED',
        'IDENTITY_USER_CREATED',
        'IDENTITY_CUSTOMER_DIGITAL_ACCESS_ENABLED',
        'IDENTITY_USER_STATUS_CHANGED',
        'IDENTITY_USER_ROLE_ASSIGNED',
        'IDENTITY_USER_ROLE_REMOVED',
        'STAFF_ASSISTED_CUSTOMER_CREATED',
        'ASSISTED_ORIGINATION_CASE_CREATED',
        'ASSISTED_ORIGINATION_CUSTOMER_ASSOCIATED',
        'ASSISTED_ORIGINATION_CASE_ABANDONED',
        'ASSISTED_ORIGINATION_CASE_COMPLETED',
        'INTAKE_DOCUMENT_VERSION_UPLOADED',
        'ASSISTED_ACTION_DOCUMENT_VERSION_UPLOADED',
        'OCR_JOB_CREATED',
        'OCR_RESULT_REVIEWED'
    ));

ALTER TABLE audit_events DROP CONSTRAINT chk_audit_events_entity_type,
    ADD CONSTRAINT chk_audit_events_entity_type CHECK (entity_type IN (
        'CUSTOMER_IDENTITY_VERIFICATION',
        'CUSTOMER',
        'CUSTOMER_BANK_ACCOUNT',
        'LOAN_APPLICATION',
        'SALARY_ADVANCE_LIMIT_MOVEMENT',
        'REVIEW_RECOMMENDATION',
        'APPROVAL_DECISION',
        'APPROVED_OFFER',
        'DOCUMENT_CHECKLIST',
        'DOCUMENT_CHECKLIST_ITEM',
        'DOCUMENT_VERSION',
        'DOCUMENT_REVIEW_DECISION',
        'LOAN_REVIEW_CYCLE',
        'LOAN_CORRECTION_REQUEST',
        'LOAN_CORRECTION_TASK',
        'SALARY_ADVANCE_VERIFICATION',
        'LOAN_CONTRACT',
        'REPAYMENT_TRANSACTION',
        'LOAN_ACCOUNT',
        'LOAN_SETTLEMENT',
        'LOAN_ACCOUNT_CLOSURE',
        'PARTNER_COMPANY',
        'PARTNER_EMPLOYEE_IMPORT_BATCH',
        'PARTNER_ELIGIBILITY_REVIEW',
        'LOAN_PRODUCT',
        'IDENTITY_USER',
        'ASSISTED_ORIGINATION_CASE',
        'INTAKE_DOCUMENT_VERSION',
        'ASSISTED_ACTION_DOCUMENT_VERSION',
        'OCR_JOB',
        'OCR_REVIEW'
    ));
