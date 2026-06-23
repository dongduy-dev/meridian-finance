package com.meridian.platform.partner.application.service;

import com.meridian.platform.partner.application.dto.PartnerCompanyDto;
import com.meridian.platform.partner.application.mapper.PartnerCompanyMapper;
import com.meridian.platform.partner.application.port.in.QueryPartnerCompanyUseCase;
import com.meridian.platform.partner.application.port.out.PartnerCompanyRepository;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

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

    @Override
    @Transactional(readOnly = true)
    public PartnerCompanyDto getPartnerCompanyById(UUID partnerCompanyId) {
        Objects.requireNonNull(partnerCompanyId, "partnerCompanyId must not be null");

        return partnerCompanyRepository.findById(partnerCompanyId)
                .map(partnerCompanyMapper::toDto)
                .orElseThrow(() -> new EntityNotFoundException(
                        "PARTNER_COMPANY_NOT_FOUND",
                        "Partner company was not found."
                ));
    }
}
