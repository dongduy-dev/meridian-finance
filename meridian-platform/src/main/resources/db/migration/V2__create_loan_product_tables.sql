CREATE TABLE loan_products
(
    id              UUID NOT NULL,
    product_code    VARCHAR(50) NOT NULL,
    product_type    VARCHAR(50) NOT NULL,
    name            VARCHAR(150) NOT NULL,
    description     TEXT,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    min_amount      NUMERIC(19, 2) NOT NULL,
    max_amount      NUMERIC(19, 2) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_loan_products PRIMARY KEY (id),
    CONSTRAINT uq_loan_products_product_code UNIQUE (product_code)
);

CREATE TABLE loan_product_policies
(
    id                   UUID NOT NULL,
    loan_product_id      UUID NOT NULL,
    policy_code          VARCHAR(100) NOT NULL,
    policy_config        JSONB NOT NULL DEFAULT '{}'::jsonb,
    offer_validity_days  INTEGER NOT NULL DEFAULT 7,
    active               BOOLEAN NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_loan_product_policies PRIMARY KEY (id),
    CONSTRAINT fk_loan_product_policies_loan_product
        FOREIGN KEY (loan_product_id)
        REFERENCES loan_products (id),
    CONSTRAINT uq_loan_product_policies_product_policy
        UNIQUE (loan_product_id, policy_code)
);

CREATE INDEX idx_loan_product_policies_loan_product_id
    ON loan_product_policies (loan_product_id);