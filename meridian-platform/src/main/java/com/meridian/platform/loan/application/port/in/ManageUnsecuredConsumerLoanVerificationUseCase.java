package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.CompleteUnsecuredConsumerLoanVerificationRequest;
import com.meridian.platform.loan.application.dto.UnsecuredConsumerLoanVerificationDto;

import java.util.UUID;

public interface ManageUnsecuredConsumerLoanVerificationUseCase {

    UnsecuredConsumerLoanVerificationDto startManualVerification(UUID loanApplicationId);

    UnsecuredConsumerLoanVerificationDto completeManualVerification(
            UUID loanApplicationId,
            CompleteUnsecuredConsumerLoanVerificationRequest request
    );
}
