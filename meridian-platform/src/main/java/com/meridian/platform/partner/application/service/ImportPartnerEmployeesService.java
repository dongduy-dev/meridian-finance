package com.meridian.platform.partner.application.service;

import com.meridian.platform.partner.application.dto.ImportPartnerEmployeesRequest;
import com.meridian.platform.partner.application.dto.PartnerEmployeeImportResultDto;
import com.meridian.platform.partner.application.dto.PartnerEmployeeImportRowRequest;
import com.meridian.platform.partner.application.port.in.ImportPartnerEmployeesUseCase;
import com.meridian.platform.partner.application.port.out.CustomerPartnerEmployeeLinkRepository;
import com.meridian.platform.partner.application.port.out.PartnerCompanyRepository;
import com.meridian.platform.partner.application.port.out.PartnerEligibilityReviewRepository;
import com.meridian.platform.partner.application.port.out.PartnerEmployeeImportBatchRepository;
import com.meridian.platform.partner.application.port.out.PartnerEmployeeRepository;
import com.meridian.platform.partner.domain.model.CustomerPartnerEmployeeLink;
import com.meridian.platform.partner.domain.model.EmployeeVerificationOutcome;
import com.meridian.platform.partner.domain.model.PartnerEmployee;
import com.meridian.platform.partner.domain.model.PartnerEmployeeImportBatch;
import com.meridian.platform.partner.domain.model.PartnerEmployeeImportBatchStatus;
import com.meridian.platform.partner.domain.model.PartnerEmployeeImportRejection;
import com.meridian.platform.partner.domain.model.PartnerEmployeeStatus;
import com.meridian.platform.partner.domain.service.PartnerEmployeeVerificationPolicy;
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
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ImportPartnerEmployeesService implements ImportPartnerEmployeesUseCase {

    private static final Pattern EFFECTIVE_MONTH_PATTERN =
            Pattern.compile("[0-9]{4}-(0[1-9]|1[0-2])");

    private final PartnerCompanyRepository companies;
    private final PartnerEmployeeImportBatchRepository batches;
    private final PartnerEmployeeRepository employees;
    private final CustomerPartnerEmployeeLinkRepository links;
    private final PartnerEligibilityReviewRepository reviews;
    private final CurrentUserProvider currentUserProvider;
    private final BusinessAuditPublisher auditPublisher;
    private final Clock clock;
    private final PartnerEmployeeVerificationPolicy verificationPolicy = new PartnerEmployeeVerificationPolicy();

    public ImportPartnerEmployeesService(
            PartnerCompanyRepository companies,
            PartnerEmployeeImportBatchRepository batches,
            PartnerEmployeeRepository employees,
            CustomerPartnerEmployeeLinkRepository links,
            PartnerEligibilityReviewRepository reviews,
            CurrentUserProvider currentUserProvider,
            BusinessAuditPublisher auditPublisher,
            Clock clock
    ) {
        this.companies = companies;
        this.batches = batches;
        this.employees = employees;
        this.links = links;
        this.reviews = reviews;
        this.currentUserProvider = currentUserProvider;
        this.auditPublisher = auditPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PartnerEmployeeImportResultDto importEmployees(
            UUID partnerCompanyId,
            ImportPartnerEmployeesRequest request
    ) {
        Objects.requireNonNull(partnerCompanyId, "partnerCompanyId must not be null");
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.requestId(), "requestId must not be null");
        String effectiveMonth = validEffectiveMonth(request.effectiveMonth());
        String fingerprint = fingerprint(partnerCompanyId, effectiveMonth, request.rows());

        batches.acquireRequestLock(request.requestId());
        PartnerEmployeeImportBatch replay = batches.findByRequestId(request.requestId()).orElse(null);
        if (replay != null) {
            if (!fingerprint.equals(replay.requestFingerprint())) {
                throw new BusinessStateConflictException(
                        "IDEMPOTENCY_KEY_REUSED",
                        "Import request ID was already used for a different logical command."
                );
            }
            return toResult(replay);
        }

        LocalDateTime operationTime = LocalDateTime.now(clock);
        companies.findByIdForUpdate(partnerCompanyId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "PARTNER_COMPANY_NOT_FOUND", "Partner company was not found."
                ));

        ValidationOutcome validation = validateRows(request.rows());
        UUID batchId = UUID.randomUUID();
        List<PartnerEmployee> validEmployees = validation.validRows().stream()
                .map(row -> new PartnerEmployee(
                        UUID.randomUUID(), partnerCompanyId, batchId, row.employeeCode(),
                        row.identityReference(), row.salaryAmount(), row.salaryAdvanceLimit(),
                        row.employmentStatus(), row.active()
                ))
                .toList();
        PartnerEmployeeImportBatch batch = new PartnerEmployeeImportBatch(
                batchId,
                partnerCompanyId,
                effectiveMonth,
                PartnerEmployeeImportBatchStatus.COMPLETED,
                validEmployees.size(),
                validation.rejections().size(),
                request.requestId(),
                fingerprint,
                validation.rejections()
        );
        PartnerEmployeeImportBatch saved = batches.save(batch);
        employees.saveAll(validEmployees);
        reconcileVerifiedLinks(saved, operationTime);
        publishAudit(saved, operationTime);
        return toResult(saved);
    }

    private void reconcileVerifiedLinks(
            PartnerEmployeeImportBatch savedBatch,
            LocalDateTime operationTime
    ) {
        String currentMonth = YearMonth.from(operationTime).toString();
        if (!currentMonth.equals(savedBatch.effectiveMonth())) {
            return;
        }

        PartnerEmployeeImportBatch authoritativeBatch = batches
                .findLatestCompletedByPartnerCompanyIdAndEffectiveMonth(
                        savedBatch.partnerCompanyId(), currentMonth
                )
                .orElse(null);
        if (authoritativeBatch == null || !authoritativeBatch.id().equals(savedBatch.id())) {
            return;
        }

        for (CustomerPartnerEmployeeLink link : links.findVerifiedByPartnerCompanyId(
                savedBatch.partnerCompanyId()
        )) {
            if (reviews.findPendingByCustomerIdAndPartnerCompanyId(
                    link.customerId(), savedBatch.partnerCompanyId()
            ).isPresent()) {
                continue;
            }

            List<PartnerEmployee> matchingEmployees = employees.findByVerificationEvidence(
                    savedBatch.partnerCompanyId(),
                    savedBatch.id(),
                    link.verifiedIdentityRef(),
                    link.verifiedEmployeeCode()
            );
            if (verificationPolicy.determineOutcome(matchingEmployees)
                    != EmployeeVerificationOutcome.MATCHED_ACTIVE) {
                continue;
            }

            links.save(link.refreshFromAuthoritativeImport(matchingEmployees.getFirst(), operationTime));
        }
    }

    private static String validEffectiveMonth(String value) {
        if (value == null || !EFFECTIVE_MONTH_PATTERN.matcher(value).matches()) {
            throw new BusinessRuleViolationException(
                    "INVALID_EFFECTIVE_MONTH",
                    "Effective month must be a valid year and month in YYYY-MM format."
            );
        }
        try {
            YearMonth.parse(value);
            return value;
        } catch (DateTimeParseException exception) {
            throw new BusinessRuleViolationException(
                    "INVALID_EFFECTIVE_MONTH",
                    "Effective month must be a valid year and month in YYYY-MM format."
            );
        }
    }

    private static ValidationOutcome validateRows(List<PartnerEmployeeImportRowRequest> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new BusinessRuleViolationException(
                    "PARTNER_EMPLOYEE_ROWS_REQUIRED",
                    "At least one Partner Employee row is required."
            );
        }
        Map<String, Integer> employeeCodeCounts = new HashMap<>();
        for (PartnerEmployeeImportRowRequest row : rows) {
            String code = row == null ? null : normalizedText(row.employeeCode());
            if (code != null && !code.isEmpty() && code.length() <= 50) {
                employeeCodeCounts.merge(code, 1, Integer::sum);
            }
        }

        List<ValidatedRow> validRows = new ArrayList<>();
        List<PartnerEmployeeImportRejection> rejections = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            PartnerEmployeeImportRowRequest row = rows.get(index);
            int rowIndex = index + 1;
            if (row == null) {
                reject(rejections, rowIndex, "ROW_REQUIRED", "Employee row is required.");
                continue;
            }
            String employeeCode = normalizedText(row.employeeCode());
            String identityReference = normalizedText(row.identityReference());
            PartnerEmployeeStatus employmentStatus = parseStatus(row.employmentStatus());
            if (employeeCode == null || employeeCode.isEmpty() || employeeCode.length() > 50) {
                reject(rejections, rowIndex, "INVALID_EMPLOYEE_CODE", "Employee code is required and must not exceed 50 characters.");
            } else if (employeeCodeCounts.getOrDefault(employeeCode, 0) > 1) {
                reject(rejections, rowIndex, "DUPLICATE_EMPLOYEE_CODE", "Employee code is duplicated within the import batch.");
            } else if (identityReference == null || identityReference.isEmpty() || identityReference.length() > 100) {
                reject(rejections, rowIndex, "INVALID_IDENTITY_REFERENCE", "Identity reference is required and must not exceed 100 characters.");
            } else if (!validMoney(row.salaryAmount())) {
                reject(rejections, rowIndex, "INVALID_SALARY_AMOUNT", "Salary amount must be a nonnegative monetary amount.");
            } else if (!validMoney(row.salaryAdvanceLimit())) {
                reject(rejections, rowIndex, "INVALID_SALARY_ADVANCE_LIMIT", "Salary Advance limit must be a nonnegative monetary amount.");
            } else if (employmentStatus == null) {
                reject(rejections, rowIndex, "INVALID_EMPLOYMENT_STATUS", "Employment status is not supported.");
            } else if (row.active() == null) {
                reject(rejections, rowIndex, "INVALID_ACTIVE_FLAG", "Active flag is required.");
            } else {
                validRows.add(new ValidatedRow(
                        employeeCode, identityReference, row.salaryAmount(), row.salaryAdvanceLimit(),
                        employmentStatus, row.active()
                ));
            }
        }
        return new ValidationOutcome(validRows, rejections);
    }

    private static boolean validMoney(BigDecimal value) {
        return value != null
                && value.signum() >= 0
                && value.scale() <= 2
                && value.precision() - value.scale() <= 17;
    }

    private static PartnerEmployeeStatus parseStatus(String value) {
        String normalized = normalizedText(value);
        if (normalized == null) return null;
        try {
            return PartnerEmployeeStatus.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String normalizedText(String value) {
        return value == null ? null : value.trim();
    }

    private static void reject(
            List<PartnerEmployeeImportRejection> rejections,
            int rowIndex,
            String code,
            String reason
    ) {
        rejections.add(new PartnerEmployeeImportRejection(rowIndex, code, reason));
    }

    private static String fingerprint(
            UUID partnerCompanyId,
            String effectiveMonth,
            List<PartnerEmployeeImportRowRequest> rows
    ) {
        if (rows == null || rows.isEmpty()) {
            throw new BusinessRuleViolationException(
                    "PARTNER_EMPLOYEE_ROWS_REQUIRED",
                    "At least one Partner Employee row is required."
            );
        }
        StringBuilder canonical = new StringBuilder();
        append(canonical, partnerCompanyId.toString());
        append(canonical, effectiveMonth);
        canonical.append(rows.size()).append('|');
        rows.forEach(row -> {
            if (row == null) {
                append(canonical, null);
                return;
            }
            append(canonical, normalizedText(row.employeeCode()));
            append(canonical, normalizedText(row.identityReference()));
            append(canonical, canonicalMoney(row.salaryAmount()));
            append(canonical, canonicalMoney(row.salaryAdvanceLimit()));
            String status = normalizedText(row.employmentStatus());
            append(canonical, status == null ? null : status.toUpperCase(Locale.ROOT));
            append(canonical, row.active() == null ? null : row.active().toString());
        });
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String canonicalMoney(BigDecimal value) {
        if (value == null) return null;
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.signum() == 0 ? "0" : normalized.toPlainString();
    }

    private static void append(StringBuilder target, String value) {
        if (value == null) {
            target.append("-1:|");
        } else {
            target.append(value.length()).append(':').append(value).append('|');
        }
    }

    private void publishAudit(
            PartnerEmployeeImportBatch batch,
            LocalDateTime occurredAt
    ) {
        AuthenticatedUser actor = currentUserProvider.currentUser();
        BusinessAuditPayload payload = BusinessAuditPayload.builder()
                .put(BusinessAuditPayloadKey.PARTNER_COMPANY_ID, batch.partnerCompanyId())
                .put(BusinessAuditPayloadKey.PARTNER_EMPLOYEE_IMPORT_BATCH_ID, batch.id())
                .put(BusinessAuditPayloadKey.PARTNER_EMPLOYEE_IMPORT_BATCH_STATUS, batch.status())
                .build();
        auditPublisher.publish(BusinessAuditEvent.single(
                BusinessOperationContext.user(batch.requestId(), actor.userId(), occurredAt),
                new BusinessAuditEntry(
                        BusinessAuditAction.PARTNER_EMPLOYEE_IMPORT_COMPLETED,
                        BusinessAuditEntityType.PARTNER_EMPLOYEE_IMPORT_BATCH,
                        batch.id(),
                        payload
                )
        ));
    }

    private static PartnerEmployeeImportResultDto toResult(PartnerEmployeeImportBatch batch) {
        return new PartnerEmployeeImportResultDto(
                batch.id(), batch.partnerCompanyId(), batch.effectiveMonth(), batch.status().name(),
                batch.validRowCount(), batch.invalidRowCount(),
                batch.rejections().stream()
                        .map(rejection -> new PartnerEmployeeImportResultDto.RowRejection(
                                rejection.rowIndex(), rejection.errorCode(), rejection.reason()
                        ))
                        .toList()
        );
    }

    private record ValidatedRow(
            String employeeCode,
            String identityReference,
            BigDecimal salaryAmount,
            BigDecimal salaryAdvanceLimit,
            PartnerEmployeeStatus employmentStatus,
            boolean active
    ) {
    }

    private record ValidationOutcome(
            List<ValidatedRow> validRows,
            List<PartnerEmployeeImportRejection> rejections
    ) {
    }
}
