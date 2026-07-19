package com.meridian.platform.approval.infrastructure.adapter.out.persistence;

import com.meridian.platform.approval.application.port.out.ApprovalDecisionRepository;
import com.meridian.platform.approval.domain.model.ApprovalDecision;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import org.springframework.stereotype.Repository;

@Repository
public class ApprovalDecisionRepositoryAdapter implements ApprovalDecisionRepository {

    private final JpaApprovalDecisionRepository jpaApprovalDecisionRepository;

    public ApprovalDecisionRepositoryAdapter(JpaApprovalDecisionRepository jpaApprovalDecisionRepository) {
        this.jpaApprovalDecisionRepository = jpaApprovalDecisionRepository;
    }

    @Override
    public ApprovalDecision save(ApprovalDecision approvalDecision) {
        try {
            return jpaApprovalDecisionRepository.saveAndFlush(
                    new ApprovalDecisionJpaEntity(approvalDecision)).toDomain();
        } catch (DataIntegrityViolationException exception) {
            if (isUniqueConstraint(exception, "uq_approval_decisions_recommendation")) {
                throw new BusinessStateConflictException(
                        "APPROVAL_DECISION_NOT_ALLOWED",
                        "An approval decision was already recorded for this recommendation."
                );
            }
            throw exception;
        }
    }

    private boolean isUniqueConstraint(Throwable exception, String constraint) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && "23505".equals(sqlException.getSQLState())
                    && sqlException.getMessage() != null
                    && sqlException.getMessage().contains(constraint)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
