package com.meridian.platform.approval.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaApprovalDecisionRepository extends JpaRepository<ApprovalDecisionJpaEntity, UUID> {
}
