package com.meridian.platform.partner.application.service;

import com.meridian.platform.partner.domain.model.PartnerEmployee;
import com.meridian.platform.partner.domain.port.in.QueryPartnerEmployeeUseCase;
import com.meridian.platform.partner.domain.port.out.PartnerEmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class QueryPartnerEmployeeService implements QueryPartnerEmployeeUseCase {

    private final PartnerEmployeeRepository partnerEmployeeRepository;

    public QueryPartnerEmployeeService(PartnerEmployeeRepository partnerEmployeeRepository) {
        this.partnerEmployeeRepository = partnerEmployeeRepository;
    }

    @Override
    public List<PartnerEmployee> getPartnerEmployeesByCompanyId(UUID partnerCompanyId, boolean activeOnly) {
        Objects.requireNonNull(partnerCompanyId, "partnerCompanyId must not be null");

        if (activeOnly) {
            return partnerEmployeeRepository.findActiveByPartnerCompanyId(partnerCompanyId);
        }
        else {
            return partnerEmployeeRepository.findByPartnerCompanyId(partnerCompanyId);
        }
    }
}