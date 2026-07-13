INSERT INTO permissions (id, code, description)
VALUES
    ('00000000-0000-0000-0000-000000000224', 'customer:bank-account:read:own', 'Read own customer bank accounts'),
    ('00000000-0000-0000-0000-000000000225', 'customer:bank-account:write:own', 'Manage own customer bank accounts')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission
    ON permission.code IN (
        'customer:bank-account:read:own',
        'customer:bank-account:write:own'
    )
WHERE role.code = 'CUSTOMER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

ALTER TABLE audit_events
    DROP CONSTRAINT chk_audit_events_entity_type;

ALTER TABLE audit_events
    ADD CONSTRAINT chk_audit_events_entity_type
    CHECK (
        entity_type IN (
            'CUSTOMER',
            'CUSTOMER_BANK_ACCOUNT',
            'LOAN_APPLICATION',
            'SALARY_ADVANCE_LIMIT_MOVEMENT',
            'REVIEW_RECOMMENDATION',
            'APPROVAL_DECISION',
            'APPROVED_OFFER'
        )
    );

ALTER TABLE audit_events
    DROP CONSTRAINT chk_audit_events_action;

ALTER TABLE audit_events
    ADD CONSTRAINT chk_audit_events_action
    CHECK (
        action IN (
            'CUSTOMER_PROFILE_CREATED',
            'CUSTOMER_PROFILE_UPDATED',
            'CUSTOMER_PROFILE_COMPLETED',
            'CUSTOMER_BANK_ACCOUNT_ADDED',
            'CUSTOMER_BANK_ACCOUNT_MADE_PRIMARY',
            'CUSTOMER_BANK_ACCOUNT_DEACTIVATED',
            'SALARY_ADVANCE_APPLICATION_SUBMITTED',
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
            'RESERVATION_RELEASED'
        )
    );
