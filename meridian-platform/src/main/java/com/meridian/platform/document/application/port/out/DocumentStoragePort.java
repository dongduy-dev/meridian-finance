package com.meridian.platform.document.application.port.out;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;

public interface DocumentStoragePort {

    StagedDocument stage(InputStream content, String declaredMimeType, String originalFilename);

    StoredObject commit(StagedDocument stagedDocument);

    InputStream open(String storageKey);

    void discardStaged(StagedDocument stagedDocument);

    void deleteFinal(String storageKey);

    int deleteStagingFilesOlderThan(Instant cutoff);

    List<StoredObjectCandidate> findFinalObjectsOlderThan(Instant cutoff);
}
