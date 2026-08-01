package com.meridian.platform.loan.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface OverdueEvaluationCandidateQuery {

    List<Candidate> findCandidates(LocalDate evaluationDate, int batchSize);

    record Candidate(UUID loanApplicationId, UUID loanAccountId) {
    }
}
