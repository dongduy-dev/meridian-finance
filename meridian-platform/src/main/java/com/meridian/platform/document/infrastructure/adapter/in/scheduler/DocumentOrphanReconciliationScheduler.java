package com.meridian.platform.document.infrastructure.adapter.in.scheduler;

import com.meridian.platform.document.application.port.out.DocumentRepository;
import com.meridian.platform.document.application.port.out.DocumentStoragePort;
import com.meridian.platform.document.application.port.out.StoredObjectCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
@ConditionalOnProperty(
        name = "meridian.document.orphan-reconciliation.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DocumentOrphanReconciliationScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentOrphanReconciliationScheduler.class);
    private static final Duration ORPHAN_AGE = Duration.ofHours(24);

    private final DocumentStoragePort storagePort;
    private final DocumentRepository documentRepository;
    private final Clock clock;

    public DocumentOrphanReconciliationScheduler(
            DocumentStoragePort storagePort,
            DocumentRepository documentRepository,
            Clock clock
    ) {
        this.storagePort = storagePort;
        this.documentRepository = documentRepository;
        this.clock = clock;
    }

    @Scheduled(cron = "${meridian.document.orphan-reconciliation.cron:0 15 2 * * *}", zone = "UTC")
    public void reconcile() {
        Instant cutoff = clock.instant().minus(ORPHAN_AGE);
        int deletedStaging = storagePort.deleteStagingFilesOlderThan(cutoff);
        int deletedFinal = 0;
        for (StoredObjectCandidate candidate : storagePort.findFinalObjectsOlderThan(cutoff)) {
            if (!documentRepository.existsStorageReference(candidate.storageKey())) {
                storagePort.deleteFinal(candidate.storageKey());
                deletedFinal++;
            }
        }
        LOGGER.info(
                "Document orphan reconciliation completed: deletedStagingCount={}, deletedFinalCount={}",
                deletedStaging,
                deletedFinal
        );
    }
}
