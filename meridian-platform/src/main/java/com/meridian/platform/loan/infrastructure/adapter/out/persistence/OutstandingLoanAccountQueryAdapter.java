package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.OutstandingLoanAccountQuery;
import com.meridian.platform.loan.domain.model.ProductCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Repository
public class OutstandingLoanAccountQueryAdapter implements OutstandingLoanAccountQuery {

    private final JdbcTemplate jdbc;

    public OutstandingLoanAccountQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public GuardResult inspect(UUID customerId, ProductCode productCode) {
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(productCode, "productCode must not be null");
        GuardCounts counts = jdbc.queryForObject(
                """
                SELECT
                    COUNT(*) FILTER (
                        WHERE account.status IN ('ACTIVE', 'OVERDUE')
                          AND account.total_outstanding > 0
                    ) AS outstanding_count,
                    COUNT(*) FILTER (
                        WHERE NOT (
                            (account.status IN ('ACTIVE', 'OVERDUE')
                                AND account.total_outstanding > 0)
                            OR (account.status IN ('SETTLED', 'CLOSED')
                                AND account.total_outstanding = 0)
                        )
                    ) AS inconsistent_count
                FROM loan_accounts account
                JOIN loan_applications application
                  ON application.id = account.loan_application_id
                WHERE application.customer_id = ?
                  AND application.product_code = ?
                """,
                (resultSet, rowNumber) -> new GuardCounts(
                        resultSet.getLong("outstanding_count"),
                        resultSet.getLong("inconsistent_count")
                ),
                customerId,
                productCode.name()
        );
        if (counts == null || counts.inconsistent > 0) {
            return GuardResult.INCONSISTENT;
        }
        return counts.outstanding > 0
                ? GuardResult.OUTSTANDING_EXISTS
                : GuardResult.CLEAR;
    }

    private record GuardCounts(long outstanding, long inconsistent) {
    }
}
