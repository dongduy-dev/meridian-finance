package com.meridian.platform.partner.application.service;

import com.meridian.platform.partner.application.dto.PartnerVerificationOptionDto;
import com.meridian.platform.partner.application.port.in.QueryPartnerVerificationOptionsUseCase;
import com.meridian.platform.partner.application.port.out.PartnerCompanyRepository;
import com.meridian.platform.partner.domain.model.PartnerCompanyStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class QueryPartnerVerificationOptionsService implements QueryPartnerVerificationOptionsUseCase {

    private final PartnerCompanyRepository partnerCompanies;

    public QueryPartnerVerificationOptionsService(PartnerCompanyRepository partnerCompanies) {
        this.partnerCompanies = partnerCompanies;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PartnerVerificationOptionDto> query() {
        return partnerCompanies.findAll().stream()
                .filter(company -> company.status() == PartnerCompanyStatus.ACTIVE)
                .sorted(Comparator.comparing(company -> company.companyCode().toUpperCase()))
                .map(company -> new PartnerVerificationOptionDto(
                        company.id(),
                        company.companyCode(),
                        company.name()
                ))
                .toList();
    }
}
