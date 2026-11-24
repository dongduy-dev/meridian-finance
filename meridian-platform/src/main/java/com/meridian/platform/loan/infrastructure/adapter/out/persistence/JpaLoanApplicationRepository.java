package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.OriginationChannel;
import com.meridian.platform.loan.domain.model.ProductCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaLoanApplicationRepository extends JpaRepository<LoanApplicationJpaEntity, UUID> {

    List<LoanApplicationJpaEntity> findByCustomerIdOrderBySubmittedAtDescIdDesc(UUID customerId);

    @Query("""
            select loanApplication
            from LoanApplicationJpaEntity loanApplication
            where (:productCode is null or loanApplication.productCode = :productCode)
              and (:status is null or loanApplication.status = :status)
              and (:applicationNumber is null or loanApplication.applicationNumber = :applicationNumber)
              and (:originationChannel is null or loanApplication.originationChannel = :originationChannel)
              and (cast(:submittedFrom as timestamp) is null or loanApplication.submittedAt >= :submittedFrom)
              and (cast(:submittedTo as timestamp) is null or loanApplication.submittedAt < :submittedTo)
            order by loanApplication.submittedAt desc, loanApplication.id desc
            """)
    Page<LoanApplicationJpaEntity> findStaffPage(
            @Param("productCode") ProductCode productCode,
            @Param("status") LoanApplicationStatus status,
            @Param("applicationNumber") String applicationNumber,
            @Param("originationChannel") OriginationChannel originationChannel,
            @Param("submittedFrom") LocalDateTime submittedFrom,
            @Param("submittedTo") LocalDateTime submittedTo,
            Pageable pageable
    );

    boolean existsByCustomerIdAndProductCodeAndStatusIn(
            UUID customerId,
            ProductCode productCode,
            Collection<LoanApplicationStatus> statuses
    );

    boolean existsByCustomerIdAndProductCodeAndStatusInAndIdNot(
            UUID customerId,
            ProductCode productCode,
            Collection<LoanApplicationStatus> statuses,
            UUID excludedLoanApplicationId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select loanApplication from LoanApplicationJpaEntity loanApplication where loanApplication.id = :id")
    Optional<LoanApplicationJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = "SELECT nextval('loan_application_number_seq')", nativeQuery = true)
    long nextApplicationNumberSequence();
}
