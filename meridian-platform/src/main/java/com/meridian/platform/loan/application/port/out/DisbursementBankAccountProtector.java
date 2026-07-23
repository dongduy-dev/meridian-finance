package com.meridian.platform.loan.application.port.out;

public interface DisbursementBankAccountProtector {
    ProtectedBankAccountEnvelope protect(byte[] accountNumber, DisbursementBankAccountProtectionContext context);
    byte[] revealToBytes(ProtectedBankAccountEnvelope envelope, DisbursementBankAccountProtectionContext context);
}
