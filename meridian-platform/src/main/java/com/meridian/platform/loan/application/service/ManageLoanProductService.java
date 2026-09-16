package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.dto.AdminLoanProductDto;
import com.meridian.platform.loan.application.dto.ChangeLoanProductActivationRequest;
import com.meridian.platform.loan.application.dto.UpdateLoanProductLimitsRequest;
import com.meridian.platform.loan.application.mapper.AdminLoanProductMapper;
import com.meridian.platform.loan.application.port.in.ManageLoanProductUseCase;
import com.meridian.platform.loan.application.port.out.LoanProductRepository;
import com.meridian.platform.loan.domain.model.LoanProduct;
import com.meridian.platform.loan.domain.model.ProductCode;
import com.meridian.platform.shared.application.audit.BusinessAuditEntry;
import com.meridian.platform.shared.application.audit.BusinessAuditEvent;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import com.meridian.platform.shared.domain.audit.BusinessAuditAction;
import com.meridian.platform.shared.domain.audit.BusinessAuditEntityType;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayload;
import com.meridian.platform.shared.domain.audit.BusinessAuditPayloadKey;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class ManageLoanProductService implements ManageLoanProductUseCase {
    private final LoanProductRepository products;
    private final AdminLoanProductMapper mapper;
    private final CurrentUserProvider currentUserProvider;
    private final BusinessAuditPublisher auditPublisher;
    private final Clock clock;

    public ManageLoanProductService(
            LoanProductRepository products,
            AdminLoanProductMapper mapper,
            CurrentUserProvider currentUserProvider,
            BusinessAuditPublisher auditPublisher,
            Clock clock
    ) {
        this.products = products;
        this.mapper = mapper;
        this.currentUserProvider = currentUserProvider;
        this.auditPublisher = auditPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AdminLoanProductDto updateLimits(String productCode, UpdateLoanProductLimitsRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        LoanProduct current = productForUpdate(productCode);
        LoanProduct updated = current.updateLimits(request.minAmount(), request.maxAmount());
        if (updated == current) {
            return mapper.toDto(current);
        }
        LoanProduct saved = products.save(updated);
        publish(saved, BusinessAuditAction.LOAN_PRODUCT_LIMITS_UPDATED);
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    public AdminLoanProductDto changeActivation(
            String productCode,
            ChangeLoanProductActivationRequest request
    ) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.active(), "active must not be null");
        LoanProduct current = productForUpdate(productCode);
        LoanProduct updated = current.changeActivation(request.active());
        if (updated == current) {
            return mapper.toDto(current);
        }
        LoanProduct saved = products.save(updated);
        publish(saved, saved.active()
                ? BusinessAuditAction.LOAN_PRODUCT_ACTIVATED
                : BusinessAuditAction.LOAN_PRODUCT_DEACTIVATED);
        return mapper.toDto(saved);
    }

    private LoanProduct productForUpdate(String productCode) {
        ProductCode parsed = parseProductCode(productCode);
        return products.findByProductCodeForUpdate(parsed)
                .orElseThrow(() -> new EntityNotFoundException(
                        "PRODUCT_NOT_FOUND",
                        "Product was not found."
                ));
    }

    private ProductCode parseProductCode(String productCode) {
        try {
            return ProductCode.valueOf(productCode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new EntityNotFoundException(
                    "PRODUCT_CODE_NOT_FOUND",
                    "Product code was not found."
            );
        }
    }

    private void publish(LoanProduct product, BusinessAuditAction action) {
        AuthenticatedUser actor = currentUserProvider.currentUser();
        LocalDateTime occurredAt = LocalDateTime.now(clock);
        BusinessAuditPayload payload = BusinessAuditPayload.builder()
                .put(BusinessAuditPayloadKey.PRODUCT_CODE, product.productCode())
                .build();
        auditPublisher.publish(BusinessAuditEvent.single(
                BusinessOperationContext.user(UUID.randomUUID(), actor.userId(), occurredAt),
                new BusinessAuditEntry(action, BusinessAuditEntityType.LOAN_PRODUCT, product.id(), payload)
        ));
    }
}
