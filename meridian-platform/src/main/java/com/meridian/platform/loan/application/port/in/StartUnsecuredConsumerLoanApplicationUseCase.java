package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.UnsecuredConsumerLoanApplicationDto;
import com.meridian.platform.loan.application.dto.UnsecuredConsumerLoanApplicationRequest;

public interface StartUnsecuredConsumerLoanApplicationUseCase {

    UnsecuredConsumerLoanApplicationDto startUnsecuredConsumerLoanApplication(
            UnsecuredConsumerLoanApplicationRequest request
    );
}
