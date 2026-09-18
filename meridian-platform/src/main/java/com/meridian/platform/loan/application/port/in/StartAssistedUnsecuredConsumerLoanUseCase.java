package com.meridian.platform.loan.application.port.in;

import com.meridian.platform.loan.application.dto.AssistedOriginationCaseDto;
import com.meridian.platform.loan.application.dto.UnsecuredConsumerLoanApplicationRequest;

import java.util.UUID;

public interface StartAssistedUnsecuredConsumerLoanUseCase {

    AssistedOriginationCaseDto submit(
            UUID assistedOriginationCaseId,
            UnsecuredConsumerLoanApplicationRequest request
    );
}
