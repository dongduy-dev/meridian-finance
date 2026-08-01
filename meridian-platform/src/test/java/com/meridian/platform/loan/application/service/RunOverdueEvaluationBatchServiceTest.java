package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.in.EvaluateLoanAccountOverdueUseCase;
import com.meridian.platform.loan.application.port.in.RunOverdueEvaluationBatchUseCase;
import com.meridian.platform.loan.application.port.out.OverdueEvaluationCandidateQuery;
import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RunOverdueEvaluationBatchServiceTest {

    @Test
    void processesBoundedCandidatesIndependentlyAndContinuesAfterFailure() {
        LocalDate date = LocalDate.of(2026, 8, 28);
        LocalDateTime at = date.atTime(0, 5);
        var first = candidate();
        var second = candidate();
        var third = candidate();
        List<EvaluateLoanAccountOverdueUseCase.Command> commands = new ArrayList<>();
        EvaluateLoanAccountOverdueUseCase evaluator = command -> {
            commands.add(command);
            if (command.loanAccountId().equals(first.loanAccountId())) {
                throw new IllegalStateException("corrupt candidate");
            }
            boolean noOp = command.loanAccountId().equals(second.loanAccountId());
            return new EvaluateLoanAccountOverdueUseCase.Result(
                    command.loanApplicationId(), command.loanAccountId(), date,
                    LoanAccountStatus.ACTIVE,
                    noOp ? LoanAccountStatus.ACTIVE : LoanAccountStatus.OVERDUE,
                    noOp ? 0 : 1, !noOp, noOp);
        };
        OverdueEvaluationCandidateQuery query = (target, size) -> {
            assertEquals(date, target);
            assertEquals(3, size);
            return List.of(first, second, third);
        };

        RunOverdueEvaluationBatchUseCase.Result result =
                new RunOverdueEvaluationBatchService(query, evaluator).run(
                        new RunOverdueEvaluationBatchUseCase.Command(date, at, 3));

        assertEquals(new RunOverdueEvaluationBatchUseCase.Result(3, 1, 1, 1, 1), result);
        assertEquals(List.of(first.loanAccountId(), second.loanAccountId(), third.loanAccountId()),
                commands.stream().map(EvaluateLoanAccountOverdueUseCase.Command::loanAccountId).toList());
        assertEquals(List.of(date, date, date), commands.stream()
                .map(EvaluateLoanAccountOverdueUseCase.Command::evaluationDate).toList());
        assertEquals(List.of(at, at, at), commands.stream()
                .map(EvaluateLoanAccountOverdueUseCase.Command::evaluatedAt).toList());
    }

    private static OverdueEvaluationCandidateQuery.Candidate candidate() {
        return new OverdueEvaluationCandidateQuery.Candidate(UUID.randomUUID(), UUID.randomUUID());
    }
}
