package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class LoanApplicationRepositoryAdapter implements LoanApplicationRepository {

    private static final String ACTIVE_APPLICATION_CONSTRAINT =
            "uq_loan_applications_customer_product_active";
    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

    private final JpaLoanApplicationRepository jpaLoanApplicationRepository;
    private final EntityManager entityManager;

    public LoanApplicationRepositoryAdapter(
            JpaLoanApplicationRepository jpaLoanApplicationRepository,
            EntityManager entityManager
    ) {
        this.jpaLoanApplicationRepository = jpaLoanApplicationRepository;
        this.entityManager = entityManager;
    }

    @Override
    public void acquireCustomerProductLock(UUID customerId, ProductCode productCode) {
        String lockKey = "loan-application:customer-product:" + customerId + ":" + productCode;
        entityManager.createNativeQuery("""
                        WITH lock AS (
                            SELECT pg_advisory_xact_lock(hashtextextended(CAST(:lockKey AS text), 0))
                        )
                        SELECT 1 FROM lock
                        """)
                .setParameter("lockKey", lockKey)
                .getSingleResult();
    }

    @Override
    public LoanApplication save(LoanApplication loanApplication) {
        Optional<LoanApplicationJpaEntity> existingEntity = jpaLoanApplicationRepository.findById(
                loanApplication.id()
        );
        if (existingEntity.isPresent()) {
            LoanApplicationJpaEntity entity = existingEntity.orElseThrow();
            entity.updateFrom(loanApplication);
            return toDomain(jpaLoanApplicationRepository.save(entity));
        }

        try {
            return toDomain(jpaLoanApplicationRepository.saveAndFlush(
                    new LoanApplicationJpaEntity(loanApplication)
            ));
        } catch (DataIntegrityViolationException exception) {
            if (isActiveApplicationConstraintViolation(exception)) {
                throw blockingApplicationExists();
            }
            throw exception;
        }
    }

    @Override
    public Optional<LoanApplication> findById(UUID loanApplicationId) {
        return jpaLoanApplicationRepository.findById(loanApplicationId)
                .map(this::toDomain);
    }

    @Override
    public Optional<LoanApplication> findByIdForUpdate(UUID loanApplicationId) {
        return jpaLoanApplicationRepository.findByIdForUpdate(loanApplicationId)
                .map(this::toDomain);
    }

    @Override
    public boolean existsByCustomerIdAndProductCodeAndStatusIn(
            UUID customerId,
            ProductCode productCode,
            Set<LoanApplicationStatus> statuses
    ) {
        return jpaLoanApplicationRepository.existsByCustomerIdAndProductCodeAndStatusIn(
                customerId,
                productCode,
                statuses
        );
    }

    @Override
    public long nextApplicationNumberSequence() {
        return jpaLoanApplicationRepository.nextApplicationNumberSequence();
    }

    private boolean isActiveApplicationConstraintViolation(DataIntegrityViolationException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && UNIQUE_VIOLATION_SQL_STATE.equals(sqlException.getSQLState())
                    && messageContainsActiveApplicationConstraint(sqlException)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean messageContainsActiveApplicationConstraint(SQLException exception) {
        return exception.getMessage() != null
                && exception.getMessage().contains(ACTIVE_APPLICATION_CONSTRAINT);
    }

    private BusinessStateConflictException blockingApplicationExists() {
        return new BusinessStateConflictException(
                "BLOCKING_APPLICATION_EXISTS",
                "A blocking Salary Advance application already exists for this customer."
        );
    }

    private LoanApplication toDomain(LoanApplicationJpaEntity entity) {
        return new LoanApplication(
                entity.getId(),
                entity.getCustomerId(),
                entity.getLoanProductId(),
                entity.getApplicationNumber(),
                entity.getProductCode(),
                entity.getProductType(),
                entity.getStatus(),
                entity.getRequestedAmount(),
                entity.getRequestedTermMonths(),
                entity.getSubmittedAt()
        );
    }
}
