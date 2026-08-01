package com.meridian.platform.loan.infrastructure.adapter.in.scheduler;

import com.meridian.platform.loan.application.port.in.RunOverdueEvaluationBatchUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@ConditionalOnProperty(
        prefix = "meridian.loan.overdue-evaluation",
        name = "enabled",
        havingValue = "true"
)
public class LoanAccountOverdueScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            LoanAccountOverdueScheduler.class
    );

    private final RunOverdueEvaluationBatchUseCase batch;
    private final Clock clock;
    private final int batchSize;

    public LoanAccountOverdueScheduler(
            RunOverdueEvaluationBatchUseCase batch,
            Clock clock,
            @Value("${meridian.loan.overdue-evaluation.batch-size:100}") int batchSize
    ) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Overdue evaluation batch size must be positive.");
        }
        this.batch = batch;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Scheduled(
            cron = "${meridian.loan.overdue-evaluation.cron:0 5 0 * * *}",
            zone = "UTC"
    )
    public void evaluateOverdueAccounts() {
        Instant runInstant = clock.instant();
        LocalDate evaluationDate = LocalDate.ofInstant(runInstant, ZoneOffset.UTC);
        LocalDateTime evaluatedAt = LocalDateTime.ofInstant(runInstant, ZoneOffset.UTC);
        RunOverdueEvaluationBatchUseCase.Result result = batch.run(
                new RunOverdueEvaluationBatchUseCase.Command(
                        evaluationDate, evaluatedAt, batchSize
                )
        );
        LOGGER.info(
                "LoanAccount overdue evaluation completed: candidates={}, evaluated={}, "
                        + "noOp={}, transitioned={}, failed={}",
                result.candidates(), result.evaluated(), result.noOp(),
                result.transitioned(), result.failed()
        );
    }
}
