INSERT INTO partner_employee_import_batches (
    id,
    partner_company_id,
    effective_month,
    status,
    valid_row_count,
    invalid_row_count
)
VALUES
    (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1',
        '11111111-1111-1111-1111-111111111111',
        '2026-06',
        'COMPLETED',
        3,
        0
    ),
    (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2',
        '22222222-2222-2222-2222-222222222222',
        '2026-06',
        'COMPLETED',
        3,
        1
    ),
    (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3',
        '33333333-3333-3333-3333-333333333333',
        '2026-06',
        'COMPLETED',
        3,
        0
    )
ON CONFLICT (id) DO UPDATE
SET
    partner_company_id = EXCLUDED.partner_company_id,
    effective_month = EXCLUDED.effective_month,
    status = EXCLUDED.status,
    valid_row_count = EXCLUDED.valid_row_count,
    invalid_row_count = EXCLUDED.invalid_row_count,
    updated_at = CURRENT_TIMESTAMP;


INSERT INTO partner_employees (
    id,
    partner_company_id,
    import_batch_id,
    employee_code,
    identity_reference,
    salary_amount,
    salary_advance_limit,
    employment_status,
    active
)
VALUES
    (
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01',
        '11111111-1111-1111-1111-111111111111',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1',
        'MER-EMP-001',
        'IDREF-MER-001',
        18000000.00,
        6000000.00,
        'ACTIVE',
        TRUE
    ),
    (
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb02',
        '11111111-1111-1111-1111-111111111111',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1',
        'MER-EMP-002',
        'IDREF-MER-002',
        22000000.00,
        8000000.00,
        'ACTIVE',
        TRUE
    ),
    (
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb03',
        '11111111-1111-1111-1111-111111111111',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1',
        'MER-EMP-003',
        'IDREF-MER-003',
        15000000.00,
        5000000.00,
        'INACTIVE',
        FALSE
    ),

    (
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb04',
        '22222222-2222-2222-2222-222222222222',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2',
        'AUR-EMP-001',
        'IDREF-AUR-001',
        25000000.00,
        9000000.00,
        'ACTIVE',
        TRUE
    ),
    (
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb05',
        '22222222-2222-2222-2222-222222222222',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2',
        'AUR-EMP-002',
        'IDREF-AUR-002',
        30000000.00,
        12000000.00,
        'ACTIVE',
        TRUE
    ),
    (
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb06',
        '22222222-2222-2222-2222-222222222222',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2',
        'AUR-EMP-003',
        'IDREF-AUR-003',
        17000000.00,
        7000000.00,
        'SUSPENDED',
        FALSE
    ),

    (
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb07',
        '33333333-3333-3333-3333-333333333333',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3',
        'NOV-EMP-001',
        'IDREF-NOV-001',
        28000000.00,
        10000000.00,
        'ACTIVE',
        TRUE
    ),
    (
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb08',
        '33333333-3333-3333-3333-333333333333',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3',
        'NOV-EMP-002',
        'IDREF-NOV-002',
        35000000.00,
        15000000.00,
        'ACTIVE',
        TRUE
    ),
    (
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb09',
        '33333333-3333-3333-3333-333333333333',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3',
        'NOV-EMP-003',
        'IDREF-NOV-003',
        16000000.00,
        6000000.00,
        'TERMINATED',
        FALSE
    )
ON CONFLICT (partner_company_id, import_batch_id, employee_code) DO UPDATE
SET
    identity_reference = EXCLUDED.identity_reference,
    salary_amount = EXCLUDED.salary_amount,
    salary_advance_limit = EXCLUDED.salary_advance_limit,
    employment_status = EXCLUDED.employment_status,
    active = EXCLUDED.active,
    updated_at = CURRENT_TIMESTAMP;