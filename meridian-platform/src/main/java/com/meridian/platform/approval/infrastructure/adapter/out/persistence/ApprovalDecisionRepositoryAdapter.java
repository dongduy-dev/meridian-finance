package com.meridian.platform.approval.infrastructure.adapter.out.persistence;

import com.meridian.platform.approval.application.port.out.ApprovalDecisionRepository;
import com.meridian.platform.approval.domain.model.ApprovalDecision;
import org.springframework.stereotype.Repository;

@Repository
public class ApprovalDecisionRepositoryAdapter implements ApprovalDecisionRepository {

    private final JpaApprovalDecisionRepository jpaApprovalDecisionRepository;

    public ApprovalDecisionRepositoryAdapter(JpaApprovalDecisionRepository jpaApprovalDecisionRepository) {
        this.jpaApprovalDecisionRepository = jpaApprovalDecisionRepository;
    }

    @Override
    public ApprovalDecision save(ApprovalDecision approvalDecision) {
        return jpaApprovalDecisionRepository.save(new ApprovalDecisionJpaEntity(approvalDecision))
                .toDomain();
    }
}
