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
                SELECT loan_application_id, id
                FROM loan_accounts
                WHERE status IN ('ACTIVE', 'OVERDUE')
                  AND total_outstanding > 0
                  AND servicing_evaluation_date < ?
                ORDER BY servicing_evaluation_date, id
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
