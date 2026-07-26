INSERT INTO permissions (id, code, description)
VALUES
    ('00000000-0000-0000-0000-000000000240', 'loan:contract:acknowledge:own',
     'Acknowledge the current operational loan contract'),
    ('00000000-0000-0000-0000-000000000241', 'loan:contract:prepare',
     'Prepare or regenerate an operational loan contract'),
    ('00000000-0000-0000-0000-000000000242', 'loan:contract:read',
     'Read operational loan contracts and readiness'),
    ('00000000-0000-0000-0000-000000000243', 'loan:disbursement:prepare',
     'Confirm contract readiness before disbursement')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.code = 'loan:contract:acknowledge:own'
WHERE role.code = 'CUSTOMER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.code IN (
    'loan:contract:prepare',
    'loan:contract:read',
    'loan:disbursement:prepare'
)
WHERE role.code = 'ACCOUNTING_OFFICER'
ON CONFLICT (role_id, permission_id) DO NOTHING;
