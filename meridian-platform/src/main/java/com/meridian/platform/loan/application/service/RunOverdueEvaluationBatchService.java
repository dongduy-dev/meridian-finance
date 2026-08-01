package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.EvaluateLoanAccountOverdueUseCase;
import com.meridian.platform.loan.application.port.in.RunOverdueEvaluationBatchUseCase;
import com.meridian.platform.loan.application.port.out.OverdueEvaluationCandidateQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RunOverdueEvaluationBatchService
        implements RunOverdueEvaluationBatchUseCase {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            RunOverdueEvaluationBatchService.class
    );

    private final OverdueEvaluationCandidateQuery candidates;
    private final EvaluateLoanAccountOverdueUseCase evaluator;

    public RunOverdueEvaluationBatchService(
            OverdueEvaluationCandidateQuery candidates,
            EvaluateLoanAccountOverdueUseCase evaluator
    ) {
        this.candidates = candidates;
        this.evaluator = evaluator;
    }

    @Override
    public Result run(Command command) {
        var selected = candidates.findCandidates(
                command.evaluationDate(), command.batchSize()
        );
        int evaluated = 0;
        int noOp = 0;
        int transitioned = 0;
        int failed = 0;
        for (OverdueEvaluationCandidateQuery.Candidate candidate : selected) {
            try {
                EvaluateLoanAccountOverdueUseCase.Result result = evaluator.evaluate(
                        new EvaluateLoanAccountOverdueUseCase.Command(
                                candidate.loanApplicationId(), candidate.loanAccountId(),
                                command.evaluationDate(), command.evaluatedAt()
                        )
                );
                if (result.noOp()) {
                    noOp++;
                } else {
                    evaluated++;
                    if (result.accountStatusChanged()) {
                        transitioned++;
                    }
                }
            } catch (RuntimeException exception) {
                failed++;
                LOGGER.warn(
                        "Overdue evaluation failed for loanApplicationId={}, loanAccountId={}, failureType={}",
                        candidate.loanApplicationId(), candidate.loanAccountId(),
                        exception.getClass().getSimpleName()
                );
            }
        }
        return new Result(selected.size(), evaluated, noOp, transitioned, failed);
    }
}
