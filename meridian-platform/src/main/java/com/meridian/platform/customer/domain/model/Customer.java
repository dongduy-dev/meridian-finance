package com.meridian.platform.customer.domain.model;

import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record Customer(
        UUID id,
        String customerNumber,
        CustomerStatus status,
        VerificationStatus verificationStatus,
        ProfileCompletionStatus profileCompletionStatus,
        CustomerProfile profile,
        List<CustomerBankAccount> bankAccounts,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public Customer {
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }
        customerNumber = normalizeRequired(customerNumber, "customerNumber");
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        if (verificationStatus == null) {
            throw new IllegalArgumentException("verificationStatus is required");
        }
        if (profileCompletionStatus == null) {
            throw new IllegalArgumentException("profileCompletionStatus is required");
        }
        bankAccounts = List.copyOf(bankAccounts == null ? List.of() : bankAccounts);
    }

    public boolean isActive() {
        return status == CustomerStatus.ACTIVE;
    }

    public boolean hasCompleteProfile() {
        return profileCompletionStatus == ProfileCompletionStatus.COMPLETE && profile != null && profile.isComplete();
    }

    public boolean hasPrimaryActiveBankAccount() {
        return bankAccounts.stream().anyMatch(CustomerBankAccount::isPrimaryActive);
    }

    public Customer updateProfile(CustomerProfile newProfile, LocalDateTime now) {
        if (!id.equals(newProfile.customerId())) {
            throw new IllegalArgumentException("profile customerId does not match customer");
        }
        if (profile != null
                && profile.isComplete()
                && !profile.identityReference().fingerprint().equals(newProfile.identityReference().fingerprint())) {
            throw new BusinessStateConflictException("IDENTITY_REFERENCE_IMMUTABLE", "Identity reference cannot be changed after profile completion");
        }
        ProfileCompletionStatus completionStatus = newProfile.isComplete()
                ? ProfileCompletionStatus.COMPLETE
                : ProfileCompletionStatus.INCOMPLETE;
        LocalDateTime createdAtValue = newProfile.createdAt() == null ? now : newProfile.createdAt();
        CustomerProfile timestampedProfile = newProfile.withTimestamps(createdAtValue, now);
        return new Customer(
                id,
                customerNumber,
                status,
                verificationStatus,
                completionStatus,
                timestampedProfile,
                bankAccounts,
                createdAt,
                now);
    }

    public Customer addBankAccount(CustomerBankAccount account, LocalDateTime now) {
        if (!id.equals(account.customerId())) {
            throw new IllegalArgumentException("bank account customerId does not match customer");
        }
        boolean duplicateActiveAccount = bankAccounts.stream()
                .anyMatch(existing -> existing.isActive()
                        && existing.accountNumber().fingerprint().equals(account.accountNumber().fingerprint()));
        if (duplicateActiveAccount) {
            throw new BusinessStateConflictException("DUPLICATE_BANK_ACCOUNT", "An active bank account with the same account number already exists");
        }
        boolean shouldBePrimary = !hasPrimaryActiveBankAccount() || account.primaryAccount();
        List<CustomerBankAccount> updatedAccounts = new ArrayList<>();
        if (shouldBePrimary) {
            for (CustomerBankAccount existing : bankAccounts) {
                updatedAccounts.add(existing.demotePrimary(now));
            }
            updatedAccounts.add(account.makePrimary(now));
        } else {
            updatedAccounts.addAll(bankAccounts);
            updatedAccounts.add(account);
        }
        return withBankAccounts(updatedAccounts, now);
    }

    public Customer makePrimaryBankAccount(UUID bankAccountId, LocalDateTime now) {
        if (bankAccountId == null) {
            throw new IllegalArgumentException("bankAccountId is required");
        }
        boolean found = false;
        List<CustomerBankAccount> updatedAccounts = new ArrayList<>();
        for (CustomerBankAccount account : bankAccounts) {
            if (bankAccountId.equals(account.id())) {
                if (!account.isActive()) {
                    throw new BusinessStateConflictException("BANK_ACCOUNT_NOT_FOUND", "Bank account was not found for the customer");
                }
                updatedAccounts.add(account.makePrimary(now));
                found = true;
            } else {
                updatedAccounts.add(account.demotePrimary(now));
            }
        }
        if (!found) {
            throw new BusinessStateConflictException("BANK_ACCOUNT_NOT_FOUND", "Bank account was not found for the customer");
        }
        return withBankAccounts(updatedAccounts, now);
    }

    public Customer deactivateBankAccount(UUID bankAccountId, LocalDateTime now) {
        if (bankAccountId == null) {
            throw new IllegalArgumentException("bankAccountId is required");
        }
        boolean found = false;
        List<CustomerBankAccount> updatedAccounts = new ArrayList<>();
        for (CustomerBankAccount account : bankAccounts) {
            if (bankAccountId.equals(account.id())) {
                if (!account.isActive()) {
                    throw new BusinessStateConflictException("BANK_ACCOUNT_NOT_FOUND", "Bank account was not found for the customer");
                }
                updatedAccounts.add(account.deactivate(now));
                found = true;
            } else {
                updatedAccounts.add(account);
            }
        }
        if (!found) {
            throw new BusinessStateConflictException("BANK_ACCOUNT_NOT_FOUND", "Bank account was not found for the customer");
        }
        return withBankAccounts(updatedAccounts, now);
    }

    private Customer withBankAccounts(List<CustomerBankAccount> updatedBankAccounts, LocalDateTime now) {
        return new Customer(
                id,
                customerNumber,
                status,
                verificationStatus,
                profileCompletionStatus,
                profile,
                updatedBankAccounts,
                createdAt,
                now);
    }

    @Override
    public String toString() {
        return "Customer[id=" + id
                + ", customerNumber=" + customerNumber
                + ", status=" + status
                + ", verificationStatus=" + verificationStatus
                + ", profileCompletionStatus=" + profileCompletionStatus
                + ", bankAccountCount=" + bankAccounts.size()
                + "]";
    }

    private static String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}