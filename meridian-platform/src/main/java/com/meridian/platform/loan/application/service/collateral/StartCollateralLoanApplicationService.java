package com.meridian.platform.loan.application.service.collateral;

import com.meridian.platform.loan.application.dto.CollateralLoanApplicationDto;
import com.meridian.platform.loan.application.dto.CollateralLoanApplicationRequest;
import com.meridian.platform.loan.application.mapper.LoanMapper;
import com.meridian.platform.loan.application.port.in.StartCollateralLoanApplicationUseCase;
import com.meridian.platform.loan.application.port.out.CollateralLoanVerificationRepository;
import com.meridian.platform.loan.application.port.out.CollateralRepository;
import com.meridian.platform.loan.application.port.out.CustomerReadinessPort;
import com.meridian.platform.loan.application.port.out.LoanApplicationRepository;
import com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort;
import com.meridian.platform.loan.application.port.out.LoanProductRepository;
import com.meridian.platform.loan.application.service.LoanApplicationStatusTransitionRecorder;
import com.meridian.platform.loan.domain.model.OriginationChannel;
import com.meridian.platform.shared.application.audit.BusinessAuditPublisher;
import com.meridian.platform.shared.application.operation.BusinessOperationContext;
import com.meridian.platform.shared.application.security.AuthenticatedUser;
import com.meridian.platform.shared.application.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class StartCollateralLoanApplicationService implements StartCollateralLoanApplicationUseCase {

    private final LoanMapper loanMapper;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;
    private final CollateralLoanOrigination origination;

    public StartCollateralLoanApplicationService(
            LoanProductRepository loanProductRepository,
            LoanApplicationRepository loanApplicationRepository,
            CollateralRepository collateralRepository,
            LoanDocumentChecklistPort documentChecklistPort,
            CollateralLoanVerificationRepository verificationRepository,
            CustomerReadinessPort customerReadinessPort,
            LoanMapper loanMapper,
            CurrentUserProvider currentUserProvider,
            LoanApplicationStatusTransitionRecorder transitionRecorder,
            BusinessAuditPublisher businessAuditPublisher,
            Clock clock
    ) {
        this.loanMapper = loanMapper;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
        this.origination = new CollateralLoanOrigination(
                loanProductRepository, loanApplicationRepository, collateralRepository,
                documentChecklistPort, verificationRepository, customerReadinessPort,
                transitionRecorder, businessAuditPublisher);
    }

    @Override
    @Transactional
    public CollateralLoanApplicationDto startCollateralLoanApplication(
            CollateralLoanApplicationRequest request
    ) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.requestedAmount(), "requestedAmount must not be null");
        Objects.requireNonNull(request.requestedTermMonths(), "requestedTermMonths must not be null");
        Objects.requireNonNull(
                request.collateral(),
                "collateral must not be null"
        );

        AuthenticatedUser currentUser = currentUserProvider.currentUser();
        UUID customerId = currentUser.requireCustomerId();
        LocalDateTime now = LocalDateTime.now(clock);
        BusinessOperationContext operationContext = BusinessOperationContext.user(
                UUID.randomUUID(),
                currentUser.userId(),
                now
        );
        CollateralLoanOrigination.Result result = origination.create(
                customerId, request.requestedAmount(), request.requestedTermMonths(),
                request.collateral(), OriginationChannel.CUSTOMER_DIGITAL, operationContext, now);

        return loanMapper.toCollateralLoanApplicationDto(
                result.application(), result.collateral(), result.verification(), result.checklist()
        );
    }
}
