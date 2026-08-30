package com.meridian.platform.partner.application.service;

import com.meridian.platform.partner.application.port.out.PartnerCompanyRepository;
import com.meridian.platform.partner.domain.model.PartnerCompany;
import com.meridian.platform.partner.domain.model.PartnerCompanyStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QueryPartnerVerificationOptionsServiceTest {

    @Test
    void returnsOnlyActiveCompaniesInCompanyCodeOrderWithoutPolicyLimit() {
        PartnerCompanyRepository repository = new PartnerCompanyRepository() {
            @Override public List<PartnerCompany> findAll() {
                return List.of(company("ZETA", PartnerCompanyStatus.ACTIVE),
                        company("INACTIVE", PartnerCompanyStatus.INACTIVE),
                        company("ALPHA", PartnerCompanyStatus.ACTIVE));
            }
            @Override public java.util.Optional<PartnerCompany> findById(UUID id) {
                return java.util.Optional.empty();
            }
        };

        var result = new QueryPartnerVerificationOptionsService(repository).query();

        assertEquals(List.of("ALPHA", "ZETA"), result.stream()
                .map(option -> option.companyCode()).toList());
        assertEquals(3, result.getFirst().getClass().getRecordComponents().length);
    }

    private static PartnerCompany company(String code, PartnerCompanyStatus status) {
        return new PartnerCompany(UUID.randomUUID(), code, code + " Ltd", status,
                BigDecimal.valueOf(20_000_000));
    }
}
