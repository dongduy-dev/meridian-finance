package com.meridian.platform.loan.infrastructure.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

interface JpaManualDisbursementRepository
        extends JpaRepository<ManualDisbursementJpaEntity, UUID> {

    Optional<ManualDisbursementJpaEntity> findByRequestId(UUID requestId);

    Optional<ManualDisbursementJpaEntity> findByLoanApplicationId(UUID loanApplicationId);

    Optional<ManualDisbursementJpaEntity> findByLoanContractId(UUID loanContractId);

    Optional<ManualDisbursementJpaEntity> findByLoanAccountId(UUID loanAccountId);

    Optional<ManualDisbursementJpaEntity> findByExternalTransferReference(
            String externalTransferReference
    );

    @Modifying
    @Query(value = """
            insert into manual_disbursements (
                id,
                loan_application_id,
                loan_contract_id,
                loan_account_id,
                request_id,
                expected_contract_version,
                external_transfer_reference,
                disbursed_amount,
                disbursement_value_date,
                first_repayment_date,
                confirmed_by_user_id,
                confirmed_at
            ) values (
                :id,
                :loanApplicationId,
                :loanContractId,
                :loanAccountId,
                :requestId,
                :expectedContractVersion,
                :externalTransferReference,
                :disbursedAmount,
                :valueDate,
                :firstRepaymentDate,
                :confirmedByUserId,
                :confirmedAt
            )
            on conflict do nothing
            """, nativeQuery = true)
    int insertIfNoConflict(
            @Param("id") UUID id,
            @Param("loanApplicationId") UUID loanApplicationId,
            @Param("loanContractId") UUID loanContractId,
            @Param("loanAccountId") UUID loanAccountId,
            @Param("requestId") UUID requestId,
            @Param("expectedContractVersion") int expectedContractVersion,
            @Param("externalTransferReference") String externalTransferReference,
            @Param("disbursedAmount") BigDecimal disbursedAmount,
            @Param("valueDate") LocalDate valueDate,
            @Param("firstRepaymentDate") LocalDate firstRepaymentDate,
            @Param("confirmedByUserId") UUID confirmedByUserId,
            @Param("confirmedAt") LocalDateTime confirmedAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select disbursement
            from ManualDisbursementJpaEntity disbursement
            where disbursement.loanApplicationId = :loanApplicationId
            """)
    Optional<ManualDisbursementJpaEntity> findByLoanApplicationIdForUpdate(
            @Param("loanApplicationId") UUID loanApplicationId
    );
}
