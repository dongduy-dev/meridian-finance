package com.meridian.platform.partner.application.service;

import com.meridian.platform.partner.application.dto.PartnerEmployeeDto;
import com.meridian.platform.partner.application.dto.CurrentPartnerEmployeeSnapshotDto;
import com.meridian.platform.partner.application.mapper.PartnerEmployeeMapper;
import com.meridian.platform.partner.application.port.in.QueryPartnerEmployeeUseCase;
import com.meridian.platform.partner.application.port.out.PartnerEmployeeRepository;
import com.meridian.platform.partner.application.port.out.PartnerEmployeeImportBatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class QueryPartnerEmployeeService implements QueryPartnerEmployeeUseCase {

    private final PartnerEmployeeRepository partnerEmployeeRepository;
    private final PartnerEmployeeImportBatchRepository importBatchRepository;
    private final PartnerEmployeeMapper partnerEmployeeMapper;
    private final Clock clock;

    public QueryPartnerEmployeeService(
            PartnerEmployeeRepository partnerEmployeeRepository,
            PartnerEmployeeImportBatchRepository importBatchRepository,
            PartnerEmployeeMapper partnerEmployeeMapper,
            Clock clock
    ) {
        this.partnerEmployeeRepository = partnerEmployeeRepository;
        this.importBatchRepository = importBatchRepository;
        this.partnerEmployeeMapper = partnerEmployeeMapper;
        this.clock = clock;
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

    @Override
    public CurrentPartnerEmployeeSnapshotDto getCurrentPartnerEmployeeSnapshot(UUID partnerCompanyId) {
        Objects.requireNonNull(partnerCompanyId, "partnerCompanyId must not be null");
        String effectiveMonth = YearMonth.now(clock).toString();
        return importBatchRepository.findLatestCompletedByPartnerCompanyIdAndEffectiveMonth(partnerCompanyId, effectiveMonth)
                .map(batch -> new CurrentPartnerEmployeeSnapshotDto(
                        partnerCompanyId,
                        effectiveMonth,
                        batch.id(),
                        partnerEmployeeRepository.findByPartnerCompanyIdAndImportBatchId(partnerCompanyId, batch.id())
                                .stream().map(partnerEmployeeMapper::toDto).toList()
                ))
                .orElseGet(() -> new CurrentPartnerEmployeeSnapshotDto(partnerCompanyId, effectiveMonth, null, List.of()));
    }
}
