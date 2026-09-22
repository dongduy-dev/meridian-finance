INSERT INTO permissions (id, code, description)
VALUES (
    '00000000-0000-0000-0000-000000000253',
    'document:upload:assisted-correction',
    'Upload Customer-provided checklist evidence for an authorized Staff-assisted correction'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permissions permission ON permission.code = 'document:upload:assisted-correction'
WHERE role.code = 'LOAN_OFFICER';
