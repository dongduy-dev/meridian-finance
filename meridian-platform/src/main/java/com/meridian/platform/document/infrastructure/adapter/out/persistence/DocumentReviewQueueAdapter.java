package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import com.meridian.platform.document.application.port.out.DocumentReviewQueuePort;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class DocumentReviewQueueAdapter implements DocumentReviewQueuePort {
    private final EntityManager entityManager;

    public DocumentReviewQueueAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<DocumentReviewQueueItem> findAwaitingReview(int offset, int limit) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT item.id,
                       checklist.loan_application_id,
                       item.document_type,
                       document.current_version_id,
                       version.uploaded_at,
                       version.uploader_actor_type
                FROM document_checklist_items item
                JOIN document_checklists checklist ON checklist.id = item.checklist_id
                JOIN documents document ON document.checklist_item_id = item.id
                JOIN document_versions version ON version.id = document.current_version_id
                WHERE item.requirement_status = 'REQUIRED'
                  AND item.current_review_decision_id IS NULL
                ORDER BY version.uploaded_at, item.id
                OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
                """)
                .setParameter("offset", offset)
                .setParameter("limit", limit)
                .getResultList();
        return rows.stream().map(row -> new DocumentReviewQueueItem(
                (UUID) row[0],
                (UUID) row[1],
                (String) row[2],
                (UUID) row[3],
                toLocalDateTime(row[4]),
                (String) row[5]
        )).toList();
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        return ((Timestamp) value).toLocalDateTime();
    }
}
