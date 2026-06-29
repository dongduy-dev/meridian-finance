package com.meridian.platform.partner.application.mapper;

import com.meridian.platform.partner.application.dto.PartnerEmployeeVerificationDto;
import com.meridian.platform.partner.domain.model.PartnerEmployeeVerificationResult;
import org.springframework.stereotype.Component;

@Component
public class PartnerEmployeeVerificationMapper {

    public PartnerEmployeeVerificationDto toDto(PartnerEmployeeVerificationResult result) {
        return new PartnerEmployeeVerificationDto(
                result.customerId(),
                result.partnerCompanyId(),
                result.partnerEmployeeId(),
                result.customerPartnerEmployeeLinkId(),
                result.outcome().name(),
                result.linkStatus() == null ? null : result.linkStatus().name(),
                result.manualReviewRequired()
        );
    }
}
