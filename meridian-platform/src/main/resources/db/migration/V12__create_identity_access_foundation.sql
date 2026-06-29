CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    email VARCHAR(255) NOT NULL,
    normalized_email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    user_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    display_name VARCHAR(150) NOT NULL,
    customer_id UUID,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_users_normalized_email
        UNIQUE (normalized_email),

    CONSTRAINT chk_users_user_type
        CHECK (user_type IN (
            'CUSTOMER',
            'STAFF'
        )),

    CONSTRAINT chk_users_status
        CHECK (status IN (
            'ACTIVE',
            'SUSPENDED',
            'DISABLED'
        )),

    CONSTRAINT chk_users_customer_mapping
        CHECK (
            (user_type = 'CUSTOMER' AND customer_id IS NOT NULL)
            OR (user_type = 'STAFF' AND customer_id IS NULL)
        )
);

CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(80) NOT NULL,
    name VARCHAR(150) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_roles_code
        UNIQUE (code)
);

CREATE TABLE permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(120) NOT NULL,
    description VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_permissions_code
        UNIQUE (code)
);

CREATE TABLE role_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_role_assignments_user
        FOREIGN KEY (user_id)
        REFERENCES users (id),

    CONSTRAINT fk_role_assignments_role
        FOREIGN KEY (role_id)
        REFERENCES roles (id),

    CONSTRAINT uq_role_assignments_user_role
        UNIQUE (user_id, role_id)
);

CREATE TABLE role_permissions (
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_role_permissions
        PRIMARY KEY (role_id, permission_id),

    CONSTRAINT fk_role_permissions_role
        FOREIGN KEY (role_id)
        REFERENCES roles (id),

    CONSTRAINT fk_role_permissions_permission
        FOREIGN KEY (permission_id)
        REFERENCES permissions (id)
);

CREATE INDEX idx_users_status
    ON users (status);

CREATE INDEX idx_users_customer_id
    ON users (customer_id);

CREATE INDEX idx_role_assignments_user_id
    ON role_assignments (user_id);

CREATE INDEX idx_role_permissions_permission_id
    ON role_permissions (permission_id);
