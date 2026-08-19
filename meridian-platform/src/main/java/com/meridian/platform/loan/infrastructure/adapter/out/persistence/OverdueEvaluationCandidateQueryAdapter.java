package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.OverdueEvaluationCandidateQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Repository
public class OverdueEvaluationCandidateQueryAdapter
        implements OverdueEvaluationCandidateQuery {

    private final JdbcTemplate jdbc;

    public OverdueEvaluationCandidateQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Candidate> findCandidates(LocalDate evaluationDate, int batchSize) {
        Objects.requireNonNull(evaluationDate, "evaluationDate must not be null");
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive.");
        }
        return jdbc.query(
                """
                SELECT account.loan_application_id, account.id
                FROM loan_accounts account
                JOIN loan_applications application
                  ON application.id = account.loan_application_id
                WHERE application.product_code IN (
                    'SALARY_ADVANCE', 'UNSECURED_CONSUMER_LOAN', 'COLLATERAL_LOAN'
                )
                  AND account.status IN ('ACTIVE', 'OVERDUE')
                  AND account.total_outstanding > 0
                  AND account.servicing_evaluation_date < ?
                ORDER BY account.servicing_evaluation_date, account.id
                LIMIT ?
                """,
                (resultSet, rowNumber) -> new Candidate(
                        resultSet.getObject("loan_application_id", java.util.UUID.class),
                        resultSet.getObject("id", java.util.UUID.class)
                ),
                evaluationDate,
                batchSize
        );
    }
}
