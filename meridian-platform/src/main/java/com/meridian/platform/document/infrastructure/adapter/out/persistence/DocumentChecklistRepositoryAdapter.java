package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import com.meridian.platform.document.application.port.out.DocumentChecklistRepository;
import com.meridian.platform.document.domain.model.DocumentChecklist;
import com.meridian.platform.document.domain.model.DocumentChecklistItem;
import com.meridian.platform.document.domain.model.DocumentChecklistItemState;
import com.meridian.platform.document.domain.model.DocumentChecklistReadiness;
import com.meridian.platform.document.domain.model.DocumentChecklistStage;
import com.meridian.platform.document.domain.model.DocumentReviewDecision;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DocumentChecklistRepositoryAdapter implements DocumentChecklistRepository {

    private final JpaDocumentChecklistRepository checklistRepository;
    private final JpaDocumentChecklistItemRepository itemRepository;
    private final JpaDocumentRepository documentRepository;
    private final JpaDocumentReviewDecisionRepository reviewDecisionRepository;

    public DocumentChecklistRepositoryAdapter(
            JpaDocumentChecklistRepository checklistRepository,
            JpaDocumentChecklistItemRepository itemRepository,
            JpaDocumentRepository documentRepository,
            JpaDocumentReviewDecisionRepository reviewDecisionRepository
    ) {
        this.checklistRepository = checklistRepository;
        this.itemRepository = itemRepository;
        this.documentRepository = documentRepository;
        this.reviewDecisionRepository = reviewDecisionRepository;
    }

    @Override
    public DocumentChecklist save(DocumentChecklist checklist) {
        DocumentChecklistJpaEntity savedChecklist = checklistRepository.save(
                new DocumentChecklistJpaEntity(checklist)
        );
        List<DocumentChecklistItem> savedItems = checklist.items().stream()
                .map(this::saveItem)
                .toList();
        return toDomain(savedChecklist, savedItems);
    }

    @Override
    public DocumentChecklistItem saveItem(DocumentChecklistItem item) {
        DocumentChecklistItemJpaEntity entity = itemRepository.findById(item.id())
                .orElseGet(() -> new DocumentChecklistItemJpaEntity(item));
        entity.updateFrom(item);
        return itemRepository.save(entity).toDomain();
    }

    @Override
    public Optional<DocumentChecklist> findByLoanApplicationIdAndStage(
            UUID loanApplicationId,
            DocumentChecklistStage stage
    ) {
        return checklistRepository.findByLoanApplicationIdAndStage(loanApplicationId, stage)
                .map(entity -> toDomain(
                        entity,
                        itemRepository.findAllByChecklistIdOrderByCreatedAt(entity.getId()).stream()
                                .map(DocumentChecklistItemJpaEntity::toDomain)
                                .toList()
                ));
    }

    @Override
    public Optional<DocumentChecklistItem> findItemById(UUID checklistItemId) {
        return itemRepository.findById(checklistItemId).map(DocumentChecklistItemJpaEntity::toDomain);
    }

    @Override
    public Optional<DocumentChecklistItem> findItemByIdForUpdate(UUID checklistItemId) {
        return itemRepository.findByIdForUpdate(checklistItemId).map(DocumentChecklistItemJpaEntity::toDomain);
    }

    @Override
    public DocumentChecklistReadiness findReadiness(
            UUID loanApplicationId,
            DocumentChecklistStage stage
    ) {
        Optional<DocumentChecklistJpaEntity> checklist = checklistRepository
                .findByLoanApplicationIdAndStage(loanApplicationId, stage);
        if (checklist.isEmpty()) {
            return DocumentChecklistReadiness.empty();
        }
        List<DocumentChecklistItemState> states = itemRepository
                .findAllByChecklistIdOrderByCreatedAt(checklist.orElseThrow().getId())
                .stream()
                .map(this::toState)
                .toList();
        return DocumentChecklistReadiness.from(states);
    }

    private DocumentChecklistItemState toState(DocumentChecklistItemJpaEntity item) {
        Optional<DocumentJpaEntity> document = documentRepository.findByChecklistItemId(item.getId());
        UUID currentVersionId = document.map(DocumentJpaEntity::getCurrentVersionId).orElse(null);
        DocumentReviewDecision currentDecision = Optional.ofNullable(item.getCurrentReviewDecisionId())
                .flatMap(reviewDecisionRepository::findById)
                .map(DocumentReviewDecisionJpaEntity::toDomain)
                .filter(decision -> decision.documentVersionId().equals(currentVersionId))
                .orElse(null);
        return new DocumentChecklistItemState(
                item.getId(),
                item.getRequirementStatus(),
                currentVersionId,
                currentDecision == null ? null : currentDecision.outcome()
        );
    }

    private DocumentChecklist toDomain(
            DocumentChecklistJpaEntity checklist,
            List<DocumentChecklistItem> items
    ) {
        return new DocumentChecklist(
                checklist.getId(),
                checklist.getLoanApplicationId(),
                checklist.getStage(),
                items,
                checklist.getCreatedAt()
        );
    }
}
