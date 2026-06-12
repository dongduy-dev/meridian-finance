# Database Index Inventory (Template & Rules)

This document establishes the indexing strategy, rules, and a template for tracking indexes in Project Meridian.

## 1. Indexing Rules & Best Practices

1.  **Primary Keys:** Tables should generally use a single-column surrogate primary key
(UUID or BIGINT), unless a composite key is justified by the domain model.
2.  **Foreign Keys:** All foreign key columns MUST be explicitly indexed to prevent full table scans during joins and cascading operations.
3.  **Unique Constraints:** Natural keys (e.g., `national_id`, `phone_number`, `email`) MUST have `UNIQUE` constraints (which implicitly create unique indexes).
4.  **Soft Deletion:** For tables using soft deletion (`deleted_at`), partial indexes SHOULD be used for frequent queries to exclude deleted rows (e.g., `CREATE INDEX idx_active_users ON users (email) WHERE deleted_at IS NULL;`).
5.  **Sorting & Pagination:** Columns frequently used in `ORDER BY` clauses for keyset pagination (e.g., `created_at`) MUST be indexed, often as composite indexes alongside filtering columns.
6.  **Full-Text Search:** Text columns requiring search MUST use GIN indexes over `tsvector` columns, not standard B-tree indexes.

## 2. Index Inventory Template

| Table Name | Index Name | Columns Included | Index Type | Partial Clause | Purpose / Supported Query |
|---|---|---|---|---|---|
| `customers` | `idx_customers_national_id_uq` | `national_id` | B-tree (Unique) | `WHERE deleted_at IS NULL` | Prevents duplicate KYC, supports login/lookup |
| `customers` | `idx_customers_phone_uq` | `phone_number` | B-tree (Unique) | `WHERE deleted_at IS NULL` | Prevents duplicate registration |
| `loans` | `idx_loans_customer_id` | `customer_id` | B-tree | None | Look up all loans for a specific customer |
| `loans` | `idx_loans_status_created` | `status`, `created_at` | B-tree | None | Supports back-office dashboard filtering and pagination |
| `outbox_events` | `idx_outbox_unprocessed` | `created_at` | B-tree | `WHERE processed_at IS NULL` | Optimizes polling for the Transactional Outbox pattern |

