package com.meridian.platform.loan.testsupport;

import com.meridian.platform.loan.domain.model.ApprovedOfferFinancialTerms;
import com.meridian.platform.loan.domain.model.InterestCalculationMethod;
import com.meridian.platform.loan.domain.model.LoanContract;
import com.meridian.platform.loan.domain.model.LoanContractRepaymentItem;
import com.meridian.platform.loan.domain.model.ProtectedDisbursementBankAccount;
import com.meridian.platform.loan.domain.model.RepaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class LoanContractTestData {

    public static final UUID APPLICATION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    public static final UUID CONTRACT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private LoanContractTestData() {
    }

    public static LoanContract prepared() {
        ApprovedOfferFinancialTerms terms = new ApprovedOfferFinancialTerms(
                money(1_000),
                1,
                InterestCalculationMethod.FLAT_ORIGINAL_PRINCIPAL,
                new BigDecimal("0.100000"),
                money(100),
                money(0),
                money(1_100),
                RepaymentMethod.ON_SALARY_DATE
        );
        return LoanContract.prepared(
                CONTRACT_ID,
                APPLICATION_ID,
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                "MCT-BBBBBBBB-BBBB-BBBB-BBBB-BBBBBBBBBBBB",
                1,
                terms,
                List.of(new LoanContractRepaymentItem(
                        UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                        UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"),
                        1,
                        money(1_000),
                        money(100),
                        money(0),
                        money(1_100)
                )),
                new ProtectedDisbursementBankAccount(
                        UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        "VCB",
                        "Vietcombank",
                        "MERIDIAN CUSTOMER",
                        "7890",
                        true,
                        true,
                        LocalDateTime.of(2026, 7, 23, 8, 0),
                        "AES-256-GCM",
                        "v1",
                        new byte[12],
                        new byte[]{1, 2, 3},
                        "DISBURSEMENT_ACCOUNT_V1"
                ),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                null,
                null,
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                LocalDateTime.of(2026, 7, 23, 8, 0),
                null
        );
    }

    public static LoanContract acknowledged() {
        return prepared().acknowledge(
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                UUID.fromString("55555555-5555-5555-5555-555555555555"),
                LocalDateTime.of(2026, 7, 23, 8, 30)
        );
    }

    public static LoanContract ready() {
        return acknowledged().confirmReady(
                UUID.fromString("66666666-6666-6666-6666-666666666666"),
                UUID.fromString("77777777-7777-7777-7777-777777777777"),
                LocalDateTime.of(2026, 7, 23, 9, 0)
        );
    }

    private static BigDecimal money(long amount) {
        return BigDecimal.valueOf(amount).setScale(2);
    }
}
