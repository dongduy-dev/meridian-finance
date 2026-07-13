package com.meridian.platform.customer.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 12, 8, 0);

    @Test
    void completeProfileMarksCustomerCompleteAndLeavesVerificationStatusSeparate() {
        Customer customer = incompleteCustomer();

        Customer updatedCustomer = customer.updateProfile(completeProfile("fingerprint-1"), NOW);

        assertTrue(updatedCustomer.hasCompleteProfile());
        assertEquals(ProfileCompletionStatus.COMPLETE, updatedCustomer.profileCompletionStatus());
        assertEquals(VerificationStatus.UNVERIFIED, updatedCustomer.verificationStatus());
    }

    @Test
    void identityReferenceCannotChangeAfterProfileIsComplete() {
        Customer customer = incompleteCustomer().updateProfile(completeProfile("fingerprint-1"), NOW);

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> customer.updateProfile(completeProfile("fingerprint-2"), NOW.plusMinutes(1))
        );

        assertEquals("IDENTITY_REFERENCE_IMMUTABLE", exception.getErrorCode());
    }

    @Test
    void firstActiveBankAccountBecomesPrimaryAndDuplicateActiveFingerprintIsRejected() {
        Customer customer = incompleteCustomer();
        CustomerBankAccount bankAccount = activeBankAccount(UUID.randomUUID(), "account-fingerprint-1", false);

        Customer withBankAccount = customer.addBankAccount(bankAccount, NOW);

        assertTrue(withBankAccount.hasPrimaryActiveBankAccount());
        assertTrue(withBankAccount.bankAccounts().getFirst().primaryAccount());

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> withBankAccount.addBankAccount(
                        activeBankAccount(UUID.randomUUID(), "account-fingerprint-1", false),
                        NOW.plusMinutes(1)
                )
        );
        assertEquals("DUPLICATE_BANK_ACCOUNT", exception.getErrorCode());
    }

    @Test
    void domainStringOutputDoesNotExposeSensitiveCustomerEvidence() {
        Customer customer = incompleteCustomer()
                .updateProfile(completeProfile("identity-fingerprint"), NOW)
                .addBankAccount(activeBankAccount(UUID.randomUUID(), "account-fingerprint", false), NOW.plusMinutes(1));

        String combinedOutput = customer + " " + customer.profile() + " " + customer.bankAccounts().getFirst();

        assertFalse(combinedOutput.contains("Jane Borrower"));
        assertFalse(combinedOutput.contains("012345678901"));
        assertFalse(combinedOutput.contains("identity-fingerprint"));
        assertFalse(combinedOutput.contains("0901234567"));
        assertFalse(combinedOutput.contains("1 Meridian Street"));
        assertFalse(combinedOutput.contains("Jane Borrower Account"));
        assertFalse(combinedOutput.contains("account-fingerprint"));
    }


    @Test
    void primaryDeactivationRequiresPrimarySwitchWhenOtherActiveAccountExists() {
        UUID primaryAccountId = UUID.randomUUID();
        UUID replacementAccountId = UUID.randomUUID();
        Customer customer = new Customer(
                CUSTOMER_ID,
                "CUS-000000001",
                CustomerStatus.ACTIVE,
                VerificationStatus.UNVERIFIED,
                ProfileCompletionStatus.COMPLETE,
                completeProfile("fingerprint-1"),
                List.of(
                        activeBankAccount(primaryAccountId, "account-fingerprint-1", true),
                        activeBankAccount(replacementAccountId, "account-fingerprint-2", false)
                ),
                NOW,
                NOW
        );

        BusinessStateConflictException exception = assertThrows(
                BusinessStateConflictException.class,
                () -> customer.deactivateBankAccount(primaryAccountId, NOW.plusMinutes(1))
        );

        assertEquals("BANK_ACCOUNT_UPDATE_NOT_ALLOWED", exception.getErrorCode());

        Customer switched = customer.makePrimaryBankAccount(replacementAccountId, NOW.plusMinutes(2));
        Customer deactivated = switched.deactivateBankAccount(primaryAccountId, NOW.plusMinutes(3));
        assertFalse(deactivated.bankAccounts().stream()
                .filter(account -> primaryAccountId.equals(account.id()))
                .findFirst()
                .orElseThrow()
                .isActive());

        Customer repeated = deactivated.deactivateBankAccount(primaryAccountId, NOW.plusMinutes(4));
        assertEquals(CustomerBankAccountStatus.DEACTIVATED, repeated.bankAccounts().stream()
                .filter(account -> primaryAccountId.equals(account.id()))
                .findFirst()
                .orElseThrow()
                .status());
    }
    private static Customer incompleteCustomer() {
        return new Customer(
                CUSTOMER_ID,
                "CUS-000000001",
                CustomerStatus.ACTIVE,
                VerificationStatus.UNVERIFIED,
                ProfileCompletionStatus.INCOMPLETE,
                null,
                List.of(),
                NOW,
                NOW
        );
    }

    private static CustomerProfile completeProfile(String fingerprint) {
        return new CustomerProfile(
                UUID.randomUUID(),
                CUSTOMER_ID,
                "Jane Borrower",
                new ProtectedSensitiveValue("ciphertext-identity", fingerprint, "8901"),
                "0901234567",
                "1 Meridian Street",
                "SALARIED",
                "Meridian Partner Co",
                true,
                true,
                null,
                null
        );
    }

    private static CustomerBankAccount activeBankAccount(UUID id, String fingerprint, boolean primaryAccount) {
        return new CustomerBankAccount(
                id,
                CUSTOMER_ID,
                "VCB",
                "Vietcombank",
                "Jane Borrower Account",
                new ProtectedSensitiveValue("ciphertext-account", fingerprint, "6789"),
                CustomerBankAccountStatus.ACTIVE,
                primaryAccount,
                NOW,
                NOW,
                null
        );
    }
}