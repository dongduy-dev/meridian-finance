package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.document.domain.model.DocumentType;
import com.meridian.platform.loan.domain.model.LoanCorrectionResponsibility;
import com.meridian.platform.loan.domain.model.LoanCorrectionScope;
import com.meridian.platform.loan.domain.model.LoanCorrectionTask;
import com.meridian.platform.loan.domain.model.LoanCorrectionTaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "loan_correction_tasks")
public class LoanCorrectionTaskJpaEntity {
    @Id private UUID id;
    @Column(name = "correction_request_id", nullable = false) private UUID correctionRequestId;
    @Column(name = "task_sequence", nullable = false) private int taskSequence;
    @Enumerated(EnumType.STRING) @Column(name = "responsible_party", nullable = false) private LoanCorrectionResponsibility responsibleParty;
    @Enumerated(EnumType.STRING) @Column(name = "scope", nullable = false) private LoanCorrectionScope scope;
    @Enumerated(EnumType.STRING) @Column(name = "document_type") private DocumentType documentType;
    @Column(name = "create_checklist_item", nullable = false) private boolean createChecklistItem;
    @Column(name = "checklist_item_id") private UUID checklistItemId;
    @Column(name = "baseline_document_version_id") private UUID baselineDocumentVersionId;
    @Column(name = "customer_instruction") private String customerInstruction;
    @Column(name = "staff_instruction") private String staffInstruction;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false) private LoanCorrectionTaskStatus status;
    @Column(name = "completed_by_user_id") private UUID completedByUserId;
    @Column(name = "completion_request_id") private UUID completionRequestId;
    @Column(name = "completed_at") private LocalDateTime completedAt;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    protected LoanCorrectionTaskJpaEntity() {
    }

    public LoanCorrectionTaskJpaEntity(LoanCorrectionTask task) {
        this.id = task.id();
        updateFrom(task);
    }

    public void updateFrom(LoanCorrectionTask task) {
        correctionRequestId = task.correctionRequestId(); taskSequence = task.sequence();
        responsibleParty = task.responsibleParty(); scope = task.scope(); documentType = task.documentType();
        createChecklistItem = task.createChecklistItem(); checklistItemId = task.checklistItemId();
        baselineDocumentVersionId = task.baselineDocumentVersionId(); customerInstruction = task.customerInstruction();
        staffInstruction = task.staffInstruction(); status = task.status(); completedByUserId = task.completedByUserId();
        completionRequestId = task.completionRequestId(); completedAt = task.completedAt(); createdAt = task.createdAt();
        updatedAt = task.completedAt() == null ? task.createdAt() : task.completedAt();
    }

    public LoanCorrectionTask toDomain() {
        return new LoanCorrectionTask(id, correctionRequestId, taskSequence, responsibleParty, scope, documentType,
                createChecklistItem, checklistItemId, baselineDocumentVersionId, customerInstruction, staffInstruction,
                status, completedByUserId, completionRequestId, completedAt, createdAt);
    }

    public UUID getCorrectionRequestId() { return correctionRequestId; }
    public LoanCorrectionResponsibility getResponsibleParty() { return responsibleParty; }
    public LoanCorrectionTaskStatus getStatus() { return status; }
    public UUID getChecklistItemId() { return checklistItemId; }
    public int getTaskSequence() { return taskSequence; }
}
