package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import com.meridian.platform.loan.domain.model.LoanAccountStatus;
import com.meridian.platform.loan.domain.model.LoanApplicationStatus;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.loan.domain.model.ProductType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface JpaLoanAccountRepository extends JpaRepository<LoanAccountJpaEntity, UUID> {

    interface StaffServicingWorkProjection {
        UUID getLoanApplicationId();

        UUID getLoanAccountId();

        String getApplicationNumber();

        String getAccountNumber();

        ProductCode getProductCode();

        ProductType getProductType();

        LoanApplicationStatus getApplicationStatus();

        LoanAccountStatus getAccountStatus();

        LocalDateTime getActivatedAt();

        BigDecimal getOriginatedPrincipal();

        BigDecimal getTotalPaid();

        BigDecimal getTotalOutstanding();

        LocalDate getServicingEvaluationDate();

        LocalDate getLastPaymentValueDate();

        LocalDateTime getLastPaymentRecordedAt();
    }

    List<LoanAccountJpaEntity> findByCustomerIdOrderByActivatedAtDescIdDesc(UUID customerId);

    Optional<LoanAccountJpaEntity> findByLoanApplicationId(UUID loanApplicationId);

    Optional<LoanAccountJpaEntity> findByLoanContractId(UUID loanContractId);

    @Query(value = """
            select application.id as loanApplicationId,
                   account.id as loanAccountId,
                   application.applicationNumber as applicationNumber,
                   account.accountNumber as accountNumber,
                   application.productCode as productCode,
                   application.productType as productType,
                   application.status as applicationStatus,
                   account.status as accountStatus,
                   account.activatedAt as activatedAt,
                   account.approvedPrincipal as originatedPrincipal,
                   account.totalPaid as totalPaid,
                   account.totalOutstanding as totalOutstanding,
                   account.servicingEvaluationDate as servicingEvaluationDate,
                   account.lastPaymentValueDate as lastPaymentValueDate,
                   account.lastPaymentRecordedAt as lastPaymentRecordedAt
            from LoanAccountJpaEntity account, LoanApplicationJpaEntity application
            where account.loanApplicationId = application.id
              and account.status in :serviceableStatuses
              and (:productCode is null or application.productCode = :productCode)
            order by account.activatedAt desc, account.id desc
            """,
            countQuery = """
            select count(account.id)
            from LoanAccountJpaEntity account, LoanApplicationJpaEntity application
            where account.loanApplicationId = application.id
              and account.status in :serviceableStatuses
              and (:productCode is null or application.productCode = :productCode)
            """)
    Page<StaffServicingWorkProjection> findStaffServicingWork(
            @Param("productCode") ProductCode productCode,
            @Param("serviceableStatuses") Collection<LoanAccountStatus> serviceableStatuses,
            Pageable pageable
    );

    @Query(value = """
            select application.id as loanApplicationId,
                   account.id as loanAccountId,
                   application.applicationNumber as applicationNumber,
                   account.accountNumber as accountNumber,
                   application.productCode as productCode,
                   application.productType as productType,
                   application.status as applicationStatus,
                   account.status as accountStatus,
                   account.activatedAt as activatedAt,
                   account.approvedPrincipal as originatedPrincipal,
                   account.totalPaid as totalPaid,
                   account.totalOutstanding as totalOutstanding,
                   account.servicingEvaluationDate as servicingEvaluationDate,
                   account.lastPaymentValueDate as lastPaymentValueDate,
                   account.lastPaymentRecordedAt as lastPaymentRecordedAt
            from LoanAccountJpaEntity account, LoanApplicationJpaEntity application
            where account.loanApplicationId = application.id
              and account.status in :serviceableStatuses
              and account.status = :accountStatus
              and (:productCode is null or application.productCode = :productCode)
            order by account.activatedAt desc, account.id desc
            """,
            countQuery = """
            select count(account.id)
            from LoanAccountJpaEntity account, LoanApplicationJpaEntity application
            where account.loanApplicationId = application.id
              and account.status in :serviceableStatuses
              and account.status = :accountStatus
              and (:productCode is null or application.productCode = :productCode)
            """)
    Page<StaffServicingWorkProjection> findStaffServicingWorkByStatus(
            @Param("productCode") ProductCode productCode,
            @Param("accountStatus") LoanAccountStatus accountStatus,
            @Param("serviceableStatuses") Collection<LoanAccountStatus> serviceableStatuses,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select account
            from LoanAccountJpaEntity account
            where account.id = :loanAccountId
            """)
    Optional<LoanAccountJpaEntity> findByIdForUpdate(
            @Param("loanAccountId") UUID loanAccountId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select account
            from LoanAccountJpaEntity account
            where account.loanApplicationId = :loanApplicationId
            """)
    Optional<LoanAccountJpaEntity> findByLoanApplicationIdForUpdate(
            @Param("loanApplicationId") UUID loanApplicationId
    );
}
