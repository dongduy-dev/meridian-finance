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
    public List<PartnerEmployee> getPartnerEmployeesByCompanyId(UUID partnerCompanyId) {
        Objects.requireNonNull(partnerCompanyId, "partnerCompanyId must not be null");

        return partnerEmployeeRepository.findByPartnerCompanyId(partnerCompanyId);
    }
}