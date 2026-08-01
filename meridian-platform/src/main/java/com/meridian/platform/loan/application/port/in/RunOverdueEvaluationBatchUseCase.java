package com.meridian.platform.loan.application.port.in;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

public interface RunOverdueEvaluationBatchUseCase {

    Result run(Command command);

    record Command(LocalDate evaluationDate, LocalDateTime evaluatedAt, int batchSize) {
        public Command {
            Objects.requireNonNull(evaluationDate);
            Objects.requireNonNull(evaluatedAt);
            if (batchSize <= 0) {
                throw new IllegalArgumentException("batchSize must be positive.");
            }
        }
    }

    record Result(
            int candidates,
            int evaluated,
            int noOp,
            int transitioned,
            int failed
    ) {
    }
}
