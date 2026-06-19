package com.meridian.platform.partner.application.service;

import com.meridian.platform.partner.application.dto.PartnerCompanyDto;
import com.meridian.platform.partner.application.mapper.PartnerCompanyMapper;
import com.meridian.platform.partner.domain.port.in.QueryPartnerCompanyUseCase;
import com.meridian.platform.partner.domain.port.out.PartnerCompanyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class QueryPartnerCompanyService implements QueryPartnerCompanyUseCase {

    private final PartnerCompanyRepository partnerCompanyRepository;
    private final PartnerCompanyMapper partnerCompanyMapper;

    public QueryPartnerCompanyService(
            PartnerCompanyRepository partnerCompanyRepository,
            PartnerCompanyMapper partnerCompanyMapper
    ) {
        this.partnerCompanyRepository = partnerCompanyRepository;
        this.partnerCompanyMapper = partnerCompanyMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PartnerCompanyDto> getPartnerCompanies() {
        return partnerCompanyRepository.findAll()
                .stream()
                .map(partnerCompanyMapper::toDto)
                .toList();
    }
}