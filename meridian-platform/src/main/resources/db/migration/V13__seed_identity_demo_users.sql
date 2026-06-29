INSERT INTO roles (id, code, name)
VALUES
    ('00000000-0000-0000-0000-000000000101', 'CUSTOMER', 'Customer'),
    ('00000000-0000-0000-0000-000000000102', 'LOAN_OFFICER', 'Loan Officer'),
    ('00000000-0000-0000-0000-000000000103', 'APPROVER', 'Approver'),
    ('00000000-0000-0000-0000-000000000104', 'ACCOUNTING_OFFICER', 'Accounting Officer'),
    ('00000000-0000-0000-0000-000000000105', 'BACK_OFFICE_ADMIN', 'Back-Office Admin');

INSERT INTO permissions (id, code, description)
VALUES
    ('00000000-0000-0000-0000-000000000201', 'loan:submit', 'Submit customer loan applications'),
    ('00000000-0000-0000-0000-000000000202', 'loan:read:own', 'Read own customer loan applications'),
    ('00000000-0000-0000-0000-000000000203', 'loan:cancel:own', 'Cancel own eligible loan applications'),
    ('00000000-0000-0000-0000-000000000204', 'partner:employee:verify:own', 'Verify own Partner employee evidence'),
    ('00000000-0000-0000-0000-000000000205', 'document:upload', 'Upload own customer documents'),
    ('00000000-0000-0000-0000-000000000206', 'document:read:own', 'Read own customer document metadata'),
    ('00000000-0000-0000-0000-000000000207', 'loan:read', 'Read staff loan work queues and applications'),
    ('00000000-0000-0000-0000-000000000208', 'loan:review', 'Review loan applications'),
    ('00000000-0000-0000-0000-000000000209', 'approval:recommend', 'Submit Loan Officer recommendations'),
    ('00000000-0000-0000-0000-000000000210', 'document:review', 'Review submitted documents'),
    ('00000000-0000-0000-0000-000000000211', 'customer:read', 'Read customer profile review snapshots'),
    ('00000000-0000-0000-0000-000000000212', 'approval:decide', 'Submit approval decisions'),
    ('00000000-0000-0000-0000-000000000213', 'document:read', 'Read document metadata for staff workflows'),
    ('00000000-0000-0000-0000-000000000214', 'audit:read', 'Read audit records'),
    ('00000000-0000-0000-0000-000000000215', 'loan:disburse', 'Confirm manual disbursement'),
    ('00000000-0000-0000-0000-000000000216', 'repayment:update', 'Record manual repayment updates'),
    ('00000000-0000-0000-0000-000000000217', 'loan:product:manage', 'Manage loan products'),
    ('00000000-0000-0000-0000-000000000218', 'partner:read', 'Read Partner administration data'),
    ('00000000-0000-0000-0000-000000000219', 'partner:manage', 'Manage Partner administration data'),
    ('00000000-0000-0000-0000-000000000220', 'identity:user:manage', 'Manage internal users and role assignments'),
    ('00000000-0000-0000-0000-000000000221', 'admin:config', 'Manage MVP system configuration');

INSERT INTO role_permissions (role_id, permission_id)
VALUES
    ('00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000201'),
    ('00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000202'),
    ('00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000203'),
    ('00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000204'),
    ('00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000205'),
    ('00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000206'),

    ('00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000207'),
    ('00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000208'),
    ('00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000209'),
    ('00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000210'),
    ('00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000211'),

    ('00000000-0000-0000-0000-000000000103', '00000000-0000-0000-0000-000000000207'),
    ('00000000-0000-0000-0000-000000000103', '00000000-0000-0000-0000-000000000212'),
    ('00000000-0000-0000-0000-000000000103', '00000000-0000-0000-0000-000000000213'),
    ('00000000-0000-0000-0000-000000000103', '00000000-0000-0000-0000-000000000214'),

    ('00000000-0000-0000-0000-000000000104', '00000000-0000-0000-0000-000000000207'),
    ('00000000-0000-0000-0000-000000000104', '00000000-0000-0000-0000-000000000215'),
    ('00000000-0000-0000-0000-000000000104', '00000000-0000-0000-0000-000000000216'),
    ('00000000-0000-0000-0000-000000000104', '00000000-0000-0000-0000-000000000213'),

    ('00000000-0000-0000-0000-000000000105', '00000000-0000-0000-0000-000000000217'),
    ('00000000-0000-0000-0000-000000000105', '00000000-0000-0000-0000-000000000218'),
    ('00000000-0000-0000-0000-000000000105', '00000000-0000-0000-0000-000000000219'),
    ('00000000-0000-0000-0000-000000000105', '00000000-0000-0000-0000-000000000220'),
    ('00000000-0000-0000-0000-000000000105', '00000000-0000-0000-0000-000000000221'),
    ('00000000-0000-0000-0000-000000000105', '00000000-0000-0000-0000-000000000214');

INSERT INTO users (
    id,
    email,
    normalized_email,
    password_hash,
    user_type,
    status,
    display_name,
    customer_id
)
VALUES
    (
        '00000000-0000-0000-0000-000000000301',
        'customer.demo@meridian.local',
        'customer.demo@meridian.local',
        '$2a$10$ZIrDsppX.yxM6fIpe.dZH.w0TpstBQIZLKo1ewQKz05eHegLYZVPW',
        'CUSTOMER',
        'ACTIVE',
        'Customer Demo',
        '99999999-9999-9999-9999-999999999999'
    ),
    (
        '00000000-0000-0000-0000-000000000302',
        'loan.officer@meridian.local',
        'loan.officer@meridian.local',
        '$2a$10$ZIrDsppX.yxM6fIpe.dZH.w0TpstBQIZLKo1ewQKz05eHegLYZVPW',
        'STAFF',
        'ACTIVE',
        'Loan Officer Demo',
        NULL
    ),
    (
        '00000000-0000-0000-0000-000000000303',
        'approver@meridian.local',
        'approver@meridian.local',
        '$2a$10$ZIrDsppX.yxM6fIpe.dZH.w0TpstBQIZLKo1ewQKz05eHegLYZVPW',
        'STAFF',
        'ACTIVE',
        'Approver Demo',
        NULL
    ),
    (
        '00000000-0000-0000-0000-000000000304',
        'accounting.officer@meridian.local',
        'accounting.officer@meridian.local',
        '$2a$10$ZIrDsppX.yxM6fIpe.dZH.w0TpstBQIZLKo1ewQKz05eHegLYZVPW',
        'STAFF',
        'ACTIVE',
        'Accounting Officer Demo',
        NULL
    ),
    (
        '00000000-0000-0000-0000-000000000305',
        'backoffice.admin@meridian.local',
        'backoffice.admin@meridian.local',
        '$2a$10$ZIrDsppX.yxM6fIpe.dZH.w0TpstBQIZLKo1ewQKz05eHegLYZVPW',
        'STAFF',
        'ACTIVE',
        'Back-Office Admin Demo',
        NULL
    );

INSERT INTO role_assignments (id, user_id, role_id)
VALUES
    ('00000000-0000-0000-0000-000000000401', '00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-000000000101'),
    ('00000000-0000-0000-0000-000000000402', '00000000-0000-0000-0000-000000000302', '00000000-0000-0000-0000-000000000102'),
    ('00000000-0000-0000-0000-000000000403', '00000000-0000-0000-0000-000000000303', '00000000-0000-0000-0000-000000000103'),
    ('00000000-0000-0000-0000-000000000404', '00000000-0000-0000-0000-000000000304', '00000000-0000-0000-0000-000000000104'),
    ('00000000-0000-0000-0000-000000000405', '00000000-0000-0000-0000-000000000305', '00000000-0000-0000-0000-000000000105');
