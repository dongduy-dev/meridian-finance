CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE loan_products
    ALTER COLUMN id SET DEFAULT gen_random_uuid();

ALTER TABLE loan_product_policies
    ALTER COLUMN id SET DEFAULT gen_random_uuid();