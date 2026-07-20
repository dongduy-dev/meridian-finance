package com.meridian.platform.document.infrastructure.adapter.out.persistence;

import com.meridian.platform.document.domain.model.DocumentChecklistStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaDocumentChecklistRepository extends JpaRepository<DocumentChecklistJpaEntity, UUID> {
    Optional<DocumentChecklistJpaEntity> findByLoanApplicationIdAndStage(
            UUID loanApplicationId,
            DocumentChecklistStage stage
    );
}
