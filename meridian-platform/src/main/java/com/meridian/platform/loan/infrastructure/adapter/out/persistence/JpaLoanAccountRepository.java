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

    interface StaffSettlementClosureWorkProjection {
        UUID getLoanApplicationId();
        UUID getLoanAccountId();
        String getApplicationNumber();
        String getAccountNumber();
        String getProductCode();
        String getProductType();
        String getApplicationStatus();
        String getAccountStatus();
        LocalDateTime getActivatedAt();
        BigDecimal getTotalPaid();
        BigDecimal getTotalOutstanding();
        LocalDate getServicingEvaluationDate();
        LocalDate getLastPaymentValueDate();
        LocalDateTime getLastPaymentRecordedAt();
        boolean getEvidenceCoherent();
        String getPayoffProvenance();
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
              and (:accountStatus is null or account.status = :accountStatus)
              and (:productCode is null or application.productCode = :productCode)
            order by account.activatedAt desc, account.id desc
            """,
            countQuery = """
            select count(account.id)
            from LoanAccountJpaEntity account, LoanApplicationJpaEntity application
            where account.loanApplicationId = application.id
              and account.status in :serviceableStatuses
              and (:accountStatus is null or account.status = :accountStatus)
              and (:productCode is null or application.productCode = :productCode)
            """)
    Page<StaffServicingWorkProjection> findStaffServicingWork(
            @Param("productCode") ProductCode productCode,
            @Param("accountStatus") LoanAccountStatus accountStatus,
            @Param("serviceableStatuses") Collection<LoanAccountStatus> serviceableStatuses,
            Pageable pageable
    );

    @Query(value = """
            select application.id as "loanApplicationId",
                   account.id as "loanAccountId",
                   application.application_number as "applicationNumber",
                   account.account_number as "accountNumber",
                   application.product_code as "productCode",
                   application.product_type as "productType",
                   application.status as "applicationStatus",
                   account.status as "accountStatus",
                   account.activated_at as "activatedAt",
                   account.total_paid as "totalPaid",
                   account.total_outstanding as "totalOutstanding",
                   account.servicing_evaluation_date as "servicingEvaluationDate",
                   account.last_payment_value_date as "lastPaymentValueDate",
                   account.last_payment_recorded_at as "lastPaymentRecordedAt",
                   (
                       application.status = 'DISBURSED'
                       and application.customer_id = account.customer_id
                       and application.product_code in (
                           'SALARY_ADVANCE', 'UNSECURED_CONSUMER_LOAN', 'COLLATERAL_LOAN'
                       )
                       and schedule.id is not null
                       and schedule.schedule_type = 'FINAL'
                       and schedule.version = 1
                       and schedule.loan_application_id = application.id
                       and schedule.loan_account_id = account.id
                       and schedule.loan_contract_id = account.loan_contract_id
                       and schedule.approved_term_months = account.approved_term_months
                       and schedule.approved_principal = account.approved_principal
                       and schedule.total_interest = account.total_interest
                       and schedule.fee_amount = account.fee_amount
                       and schedule.total_repayment_amount = account.total_repayment_amount
                       and (select count(*) from repayment_schedule_items item
                            where item.repayment_schedule_id = schedule.id)
                           = account.approved_term_months
                       and (select count(*) from repayment_installment_progress progress
                            where progress.repayment_schedule_id = schedule.id
                              and progress.loan_account_id = account.id)
                           = account.approved_term_months
                       and coalesce((select sum(progress.total_paid)
                                     from repayment_installment_progress progress
                                     where progress.repayment_schedule_id = schedule.id
                                       and progress.loan_account_id = account.id), 0)
                           = account.total_paid
                       and coalesce((select sum(progress.total_outstanding)
                                     from repayment_installment_progress progress
                                     where progress.repayment_schedule_id = schedule.id
                                       and progress.loan_account_id = account.id), 0)
                           = account.total_outstanding
                       and not exists (
                           select 1 from repayment_installment_progress progress
                           where progress.repayment_schedule_id = schedule.id
                             and progress.loan_account_id = account.id
                             and progress.servicing_evaluation_date
                                 <> account.servicing_evaluation_date
                       )
                   ) as "evidenceCoherent",
                   cast(null as varchar) as "payoffProvenance"
            from loan_accounts account
            join loan_applications application
              on application.id = account.loan_application_id
            left join repayment_schedules schedule
              on schedule.loan_account_id = account.id
            where account.status in ('ACTIVE', 'OVERDUE')
              and account.total_outstanding > 0
              and (:productCode is null or application.product_code = :productCode)
            order by account.activated_at desc, account.id desc
            """,
            countQuery = """
            select count(account.id)
            from loan_accounts account
            join loan_applications application
              on application.id = account.loan_application_id
            where account.status in ('ACTIVE', 'OVERDUE')
              and account.total_outstanding > 0
              and (:productCode is null or application.product_code = :productCode)
            """,
            nativeQuery = true)
    Page<StaffSettlementClosureWorkProjection> findStaffSettlementWork(
            @Param("productCode") String productCode,
            Pageable pageable
    );

    @Query(value = """
            select application.id as "loanApplicationId",
                   account.id as "loanAccountId",
                   application.application_number as "applicationNumber",
                   account.account_number as "accountNumber",
                   application.product_code as "productCode",
                   application.product_type as "productType",
                   application.status as "applicationStatus",
                   account.status as "accountStatus",
                   account.activated_at as "activatedAt",
                   account.total_paid as "totalPaid",
                   account.total_outstanding as "totalOutstanding",
                   account.servicing_evaluation_date as "servicingEvaluationDate",
                   account.last_payment_value_date as "lastPaymentValueDate",
                   account.last_payment_recorded_at as "lastPaymentRecordedAt",
                   (
                       application.status = 'DISBURSED'
                       and application.customer_id = account.customer_id
                       and application.product_code in (
                           'SALARY_ADVANCE', 'UNSECURED_CONSUMER_LOAN', 'COLLATERAL_LOAN'
                       )
                       and account.total_outstanding = 0
                       and schedule.id is not null
                       and schedule.schedule_type = 'FINAL'
                       and schedule.version = 1
                       and schedule.loan_application_id = application.id
                       and schedule.loan_account_id = account.id
                       and schedule.loan_contract_id = account.loan_contract_id
                       and schedule.approved_term_months = account.approved_term_months
                       and schedule.approved_principal = account.approved_principal
                       and schedule.total_interest = account.total_interest
                       and schedule.fee_amount = account.fee_amount
                       and schedule.total_repayment_amount = account.total_repayment_amount
                       and (select count(*) from repayment_schedule_items item
                            where item.repayment_schedule_id = schedule.id)
                           = account.approved_term_months
                       and (select count(*) from repayment_installment_progress progress
                            where progress.repayment_schedule_id = schedule.id
                              and progress.loan_account_id = account.id)
                           = account.approved_term_months
                       and not exists (
                           select 1 from repayment_installment_progress progress
                           where progress.repayment_schedule_id = schedule.id
                             and progress.loan_account_id = account.id
                             and (progress.status <> 'PAID'
                                  or progress.total_outstanding <> 0
                                  or progress.principal_outstanding <> 0
                                  or progress.interest_outstanding <> 0
                                  or progress.fee_outstanding <> 0
                                  or progress.servicing_evaluation_date
                                     <> account.servicing_evaluation_date)
                       )
                       and coalesce((select sum(progress.total_paid)
                                     from repayment_installment_progress progress
                                     where progress.repayment_schedule_id = schedule.id
                                       and progress.loan_account_id = account.id), 0)
                           = account.total_paid
                       and not exists (select 1 from loan_account_closures closure
                                       where closure.loan_account_id = account.id)
                       and settled_transition.to_status = 'SETTLED'
                       and settled_transition.action in (
                           'REPAYMENT_RECORDED', 'APPROVED_SETTLEMENT'
                       )
                       and repayment.id = settled_transition.operation_id
                       and repayment.loan_application_id = application.id
                       and repayment.loan_account_id = account.id
                       and repayment.repayment_schedule_id = schedule.id
                       and repayment.recorded_at = settled_transition.occurred_at
                       and repayment.recorded_by_user_id
                           = settled_transition.actor_user_id
                       and repayment.transaction_type = case
                           when settled_transition.action = 'APPROVED_SETTLEMENT'
                               then 'APPROVED_SETTLEMENT'
                           else 'REPAYMENT' end
                       and outcome.repayment_transaction_id = repayment.id
                       and outcome.loan_application_id = application.id
                       and outcome.loan_account_id = account.id
                       and outcome.repayment_schedule_id = schedule.id
                       and outcome.account_status = 'SETTLED'
                       and outcome.account_status_changed
                       and outcome.received_amount = repayment.received_amount
                       and outcome.payment_value_date = repayment.payment_value_date
                       and outcome.recorded_at = repayment.recorded_at
                       and ((settled_transition.action = 'APPROVED_SETTLEMENT'
                             and settlement.repayment_transaction_id = repayment.id
                             and settlement.loan_application_id = application.id
                             and settlement.loan_account_id = account.id
                             and settlement.settlement_amount = repayment.received_amount
                             and settlement.approved_by_user_id
                                 = repayment.recorded_by_user_id
                             and settlement.approved_at = repayment.recorded_at)
                            or (settled_transition.action = 'REPAYMENT_RECORDED'
                                and settlement.id is null))
                       and (select count(*) from loan_account_status_transitions history
                            where history.loan_account_id = account.id
                              and history.sequence_number = 1
                              and history.from_status is null) = 1
                       and not exists (
                           select 1 from loan_account_status_transitions history
                           where history.loan_account_id = account.id
                             and history.sequence_number > 1
                             and not exists (
                                 select 1 from loan_account_status_transitions prior
                                 where prior.loan_account_id = account.id
                                   and prior.sequence_number = history.sequence_number - 1
                                   and prior.to_status = history.from_status
                             )
                       )
                       and (select count(*) from repayment_transactions all_transaction
                            where all_transaction.loan_account_id = account.id)
                           = (select count(*) from repayment_operation_outcomes all_outcome
                              where all_outcome.loan_account_id = account.id)
                       and (
                           (application.product_code = 'SALARY_ADVANCE'
                            and coalesce((select sum(movement.amount)
                                          from salary_advance_limit_movements movement
                                          where movement.loan_account_id = account.id
                                            and movement.movement_type = 'DISBURSED_TO_USED'), 0)
                                = account.approved_principal
                            and coalesce((select sum(movement.amount)
                                          from salary_advance_limit_movements movement
                                          where movement.loan_account_id = account.id
                                            and movement.movement_type = 'REPAID_RELEASED'), 0)
                                = account.approved_principal)
                           or (application.product_code in (
                                   'UNSECURED_CONSUMER_LOAN', 'COLLATERAL_LOAN'
                               )
                               and not exists (
                                   select 1 from salary_advance_limit_movements movement
                                   where movement.loan_account_id = account.id
                               )
                               and not exists (
                                   select 1 from repayment_operation_outcomes release_outcome
                                   where release_outcome.loan_account_id = account.id
                                     and release_outcome.principal_released <> 0
                               ))
                       )
                   ) as "evidenceCoherent",
                   case when settled_transition.action = 'APPROVED_SETTLEMENT'
                        then 'APPROVED_SETTLEMENT'
                        when settled_transition.action = 'REPAYMENT_RECORDED'
                        then 'CONTRACTUAL_PAYOFF'
                        else null end as "payoffProvenance"
            from loan_accounts account
            join loan_applications application
              on application.id = account.loan_application_id
            left join repayment_schedules schedule
              on schedule.loan_account_id = account.id
            left join lateral (
                select history.*
                from loan_account_status_transitions history
                where history.loan_account_id = account.id
                order by history.sequence_number desc
                limit 1
            ) settled_transition on true
            left join repayment_transactions repayment
              on repayment.id = settled_transition.operation_id
            left join repayment_operation_outcomes outcome
              on outcome.repayment_transaction_id = repayment.id
            left join approved_loan_settlements settlement
              on settlement.repayment_transaction_id = repayment.id
            where account.status = 'SETTLED'
              and (:productCode is null or application.product_code = :productCode)
            order by account.activated_at desc, account.id desc
            """,
            countQuery = """
            select count(account.id)
            from loan_accounts account
            join loan_applications application
              on application.id = account.loan_application_id
            where account.status = 'SETTLED'
              and (:productCode is null or application.product_code = :productCode)
            """,
            nativeQuery = true)
    Page<StaffSettlementClosureWorkProjection> findStaffClosureWork(
            @Param("productCode") String productCode,
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
