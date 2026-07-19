ALTER TABLE loan_correction_tasks
    DROP CONSTRAINT chk_loan_correction_tasks_scope_fields;

ALTER TABLE loan_correction_tasks
    ADD CONSTRAINT chk_loan_correction_tasks_scope_fields
        CHECK (
            (
                scope = 'SUPPORTING_DOCUMENT_UPLOAD'
                AND document_type = 'RECENT_PAYSLIP'
                AND responsible_party IN ('CUSTOMER', 'STAFF')
                AND create_checklist_item
                AND checklist_item_id IS NOT NULL
                AND baseline_document_version_id IS NULL
            )
            OR (
                scope = 'DOCUMENT_REPLACEMENT'
                AND document_type = 'RECENT_PAYSLIP'
                AND responsible_party = 'CUSTOMER'
                AND NOT create_checklist_item
                AND checklist_item_id IS NOT NULL
                AND baseline_document_version_id IS NOT NULL
            )
            OR (
                scope = 'DOCUMENT_REVIEW'
                AND document_type = 'RECENT_PAYSLIP'
                AND responsible_party = 'STAFF'
                AND NOT create_checklist_item
                AND checklist_item_id IS NOT NULL
                AND baseline_document_version_id IS NOT NULL
            )
        );

CREATE INDEX idx_loan_correction_tasks_staff_queue
    ON loan_correction_tasks (status, created_at, id)
    WHERE responsible_party = 'STAFF';

INSERT INTO permissions (id, code, description)
VALUES
    ('00000000-0000-0000-0000-000000000237', 'loan:correction:staff',
     'View, complete, and resubmit authorized staff corrections'),
    ('00000000-0000-0000-0000-000000000238', 'document:upload:staff',
     'Upload documents for explicitly authorized staff correction tasks'),
    ('00000000-0000-0000-0000-000000000239', 'document:waive',
     'Waive a document using an approved controlled reason')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.code IN ('loan:correction:staff', 'document:waive')
WHERE role.code = 'LOAN_OFFICER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.code = 'document:upload:staff'
WHERE role.code = 'BACK_OFFICE_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;
