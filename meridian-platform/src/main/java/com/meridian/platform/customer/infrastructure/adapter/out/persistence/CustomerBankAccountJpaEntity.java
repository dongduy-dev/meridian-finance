package com.meridian.platform.customer.infrastructure.adapter.out.persistence;

import com.meridian.platform.customer.domain.model.CustomerBankAccount;
import com.meridian.platform.customer.domain.model.CustomerBankAccountStatus;
import com.meridian.platform.customer.domain.model.ProtectedSensitiveValue;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "customer_bank_accounts")
public class CustomerBankAccountJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "bank_code", nullable = false)
    private String bankCode;

    @Column(name = "bank_name_snapshot", nullable = false)
    private String bankNameSnapshot;

    @Column(name = "account_holder_name", nullable = false)
    private String accountHolderName;

    @Column(name = "account_number_ciphertext", nullable = false)
    private String accountNumberCiphertext;

    @Column(name = "account_number_fingerprint", nullable = false)
    private String accountNumberFingerprint;

    @Column(name = "account_number_last_four", nullable = false)
    private String accountNumberLastFour;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CustomerBankAccountStatus status;

    @Column(name = "primary_account", nullable = false)
    private boolean primaryAccount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    protected CustomerBankAccountJpaEntity() {
    }

    public CustomerBankAccountJpaEntity(CustomerBankAccount account) {
        this.id = account.id() == null ? UUID.randomUUID() : account.id();
        apply(account);
        this.createdAt = account.createdAt() == null ? LocalDateTime.now() : account.createdAt();
        this.updatedAt = account.updatedAt() == null ? LocalDateTime.now() : account.updatedAt();
    }

    public void updateFrom(CustomerBankAccount account) {
        apply(account);
        this.updatedAt = account.updatedAt() == null ? LocalDateTime.now() : account.updatedAt();
    }

    private void apply(CustomerBankAccount account) {
        this.customerId = account.customerId();
        this.bankCode = account.bankCode();
        this.bankNameSnapshot = account.bankNameSnapshot();
        this.accountHolderName = account.accountHolderName();
        this.accountNumberCiphertext = account.accountNumber().ciphertext();
        this.accountNumberFingerprint = account.accountNumber().fingerprint();
        this.accountNumberLastFour = account.accountNumber().lastFour();
        this.status = account.status();
        this.primaryAccount = account.primaryAccount();
        this.deactivatedAt = account.deactivatedAt();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getBankCode() {
        return bankCode;
    }

    public String getBankNameSnapshot() {
        return bankNameSnapshot;
    }

    public String getAccountHolderName() {
        return accountHolderName;
    }

    public String getAccountNumberCiphertext() {
        return accountNumberCiphertext;
    }

    public String getAccountNumberFingerprint() {
        return accountNumberFingerprint;
    }

    public String getAccountNumberLastFour() {
        return accountNumberLastFour;
    }

    public CustomerBankAccountStatus getStatus() {
        return status;
    }

    public boolean isPrimaryAccount() {
        return primaryAccount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getDeactivatedAt() {
        return deactivatedAt;
    }

    public CustomerBankAccount toDomain() {
        return new CustomerBankAccount(
                id,
                customerId,
                bankCode,
                bankNameSnapshot,
                accountHolderName,
                new ProtectedSensitiveValue(accountNumberCiphertext, accountNumberFingerprint, accountNumberLastFour),
                status,
                primaryAccount,
                createdAt,
                updatedAt,
                deactivatedAt
        );
    }
}