package com.meridian.platform.partner.application.service;

import com.meridian.platform.partner.application.dto.PartnerEmployeeDto;
import com.meridian.platform.partner.application.mapper.PartnerEmployeeMapper;
import com.meridian.platform.partner.application.port.in.QueryPartnerEmployeeUseCase;
import com.meridian.platform.partner.application.port.out.PartnerEmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class QueryPartnerEmployeeService implements QueryPartnerEmployeeUseCase {

    private final PartnerEmployeeRepository partnerEmployeeRepository;
    private final PartnerEmployeeMapper partnerEmployeeMapper;

    public QueryPartnerEmployeeService(
            PartnerEmployeeRepository partnerEmployeeRepository,
            PartnerEmployeeMapper partnerEmployeeMapper
    ) {
        this.partnerEmployeeRepository = partnerEmployeeRepository;
        this.partnerEmployeeMapper = partnerEmployeeMapper;
    }

    @Override
    public List<PartnerEmployeeDto> getPartnerEmployeesByCompanyId(UUID partnerCompanyId, boolean activeOnly) {
        Objects.requireNonNull(partnerCompanyId, "partnerCompanyId must not be null");

        if (activeOnly) {
            return partnerEmployeeRepository.findActiveByPartnerCompanyId(partnerCompanyId)
                    .stream()
                    .map(partnerEmployeeMapper::toDto)
                    .toList();
        }

        return partnerEmployeeRepository.findByPartnerCompanyId(partnerCompanyId)
                .stream()
                .map(partnerEmployeeMapper::toDto)
                .toList();
    }
}
