DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM loan_applications application_row
        LEFT JOIN loan_products product
            ON product.id = application_row.loan_product_id
        WHERE product.id IS NULL
    ) THEN
        RAISE EXCEPTION
            'V30 preflight failed: Loan Application references a missing Loan product';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM loan_applications application_row
        JOIN loan_products product
            ON product.id = application_row.loan_product_id
        WHERE application_row.product_code IS DISTINCT FROM product.product_code
    ) THEN
        RAISE EXCEPTION
            'V30 preflight failed: Loan Application product code does not match its Loan product';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM loan_applications application_row
        JOIN loan_products product
            ON product.id = application_row.loan_product_id
        WHERE application_row.product_type IS DISTINCT FROM product.product_type
    ) THEN
        RAISE EXCEPTION
            'V30 preflight failed: Loan Application product type does not match its Loan product';
    END IF;

    IF EXISTS (
        SELECT product.id
        FROM loan_products product
        GROUP BY product.id
        HAVING count(DISTINCT (product.product_code, product.product_type)) <> 1
    ) THEN
        RAISE EXCEPTION
            'V30 preflight failed: Loan product identity tuple is incompatible';
    END IF;
END;
$$;

ALTER TABLE loan_products
    ADD CONSTRAINT uq_loan_products_identity_tuple
        UNIQUE (id, product_code, product_type);

ALTER TABLE loan_applications
    ADD CONSTRAINT fk_loan_applications_product_identity
        FOREIGN KEY (loan_product_id, product_code, product_type)
        REFERENCES loan_products (id, product_code, product_type)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT;

CREATE OR REPLACE FUNCTION reject_loan_application_product_identity_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.loan_product_id IS DISTINCT FROM OLD.loan_product_id
            OR NEW.product_code IS DISTINCT FROM OLD.product_code
            OR NEW.product_type IS DISTINCT FROM OLD.product_type THEN
        RAISE EXCEPTION 'Loan Application product identity is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_loan_applications_product_identity_immutable
BEFORE UPDATE OF loan_product_id, product_code, product_type ON loan_applications
FOR EACH ROW
EXECUTE FUNCTION reject_loan_application_product_identity_mutation();
