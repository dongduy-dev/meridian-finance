WITH ranked_verified_links AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY customer_id
               ORDER BY last_refreshed_at DESC, id ASC
           ) AS current_rank
    FROM customer_partner_employee_links
    WHERE link_status = 'VERIFIED'
)
UPDATE customer_partner_employee_links link
SET link_status = 'DISABLED',
    updated_at = CURRENT_TIMESTAMP
FROM ranked_verified_links ranked
WHERE link.id = ranked.id
  AND ranked.current_rank > 1;

DROP INDEX uq_customer_partner_employee_links_current_verified;

CREATE UNIQUE INDEX uq_customer_partner_employee_links_current_verified
    ON customer_partner_employee_links (customer_id)
    WHERE link_status = 'VERIFIED';

CREATE INDEX idx_partner_eligibility_reviews_pending_customer_month
    ON partner_eligibility_reviews (customer_id, effective_month)
    WHERE status = 'PENDING';
