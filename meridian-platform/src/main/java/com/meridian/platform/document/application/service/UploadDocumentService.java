package com.meridian.platform.document.application.service;

import com.meridian.platform.document.application.dto.DocumentVersionDto;
import com.meridian.platform.document.application.dto.UploadDocumentCommand;
import com.meridian.platform.document.application.port.in.UploadDocumentUseCase;
import com.meridian.platform.document.application.port.out.DocumentStoragePort;
import com.meridian.platform.document.application.port.out.StagedDocument;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class UploadDocumentService implements UploadDocumentUseCase {

    private final DocumentStoragePort storagePort;
    private final TransactionalDocumentUploadService transactionalUploadService;

    public UploadDocumentService(
            DocumentStoragePort storagePort,
            TransactionalDocumentUploadService transactionalUploadService
    ) {
        this.storagePort = storagePort;
        this.transactionalUploadService = transactionalUploadService;
    }

    @Override
    public DocumentVersionDto upload(UploadDocumentCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        StagedDocument staged = storagePort.stage(
                command.content(),
                command.declaredMimeType(),
                command.originalFilename()
        );
        try {
            return transactionalUploadService.store(command, staged);
        } finally {
            storagePort.discardStaged(staged);
        }
    }
}
