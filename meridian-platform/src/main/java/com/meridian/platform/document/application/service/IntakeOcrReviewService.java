package com.meridian.platform.document.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.platform.document.application.dto.FinalizeIntakeOcrReviewRequest;
import com.meridian.platform.document.application.dto.IntakeOcrFieldSuggestionDto;
import com.meridian.platform.document.application.dto.IntakeOcrReviewDto;
import com.meridian.platform.document.application.port.in.ManageIntakeOcrReviewUseCase;
import com.meridian.platform.document.application.port.out.IntakeDocumentRepository;
import com.meridian.platform.document.application.port.out.LoanAssistedOriginationPort;
import com.meridian.platform.document.application.port.out.OcrJobRepository;
import com.meridian.platform.document.application.port.out.OcrResultCipher;
import com.meridian.platform.document.application.port.out.OcrResultRepository;
import com.meridian.platform.document.application.port.out.OcrReviewRepository;
import com.meridian.platform.document.domain.model.IntakeDocument;
import com.meridian.platform.document.domain.model.IntakeEvidenceType;
import com.meridian.platform.document.domain.model.OcrJob;
import com.meridian.platform.document.domain.model.OcrJobState;
import com.meridian.platform.document.domain.model.OcrResult;
import com.meridian.platform.document.domain.model.OcrResultDisposition;
import com.meridian.platform.document.domain.model.OcrReview;
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
import com.meridian.platform.shared.domain.exception.AuthorizationException;
import com.meridian.platform.shared.domain.exception.BusinessRuleViolationException;
import com.meridian.platform.shared.domain.exception.BusinessStateConflictException;
import com.meridian.platform.shared.domain.exception.EntityNotFoundException;
import com.meridian.platform.shared.domain.exception.ServiceUnavailableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class IntakeOcrReviewService implements ManageIntakeOcrReviewUseCase {

    private static final String PERMISSION = "document:upload:intake";
    private static final int MAX_REVIEWED_PAYLOAD_CHARS = 20_000;

    private final IntakeDocumentRepository documents;
    private final OcrJobRepository jobs;
    private final OcrResultRepository results;
    private final OcrReviewRepository reviews;
    private final OcrResultCipher cipher;
    private final LoanAssistedOriginationPort assistedOriginations;
    private final CurrentUserProvider currentUsers;
    private final BusinessAuditPublisher auditPublisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public IntakeOcrReviewService(
            IntakeDocumentRepository documents,
            OcrJobRepository jobs,
            OcrResultRepository results,
            OcrReviewRepository reviews,
            OcrResultCipher cipher,
            LoanAssistedOriginationPort assistedOriginations,
            CurrentUserProvider currentUsers,
            BusinessAuditPublisher auditPublisher,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.documents = documents;
        this.jobs = jobs;
        this.results = results;
        this.reviews = reviews;
        this.cipher = cipher;
        this.assistedOriginations = assistedOriginations;
        this.currentUsers = currentUsers;
        this.auditPublisher = auditPublisher;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public IntakeOcrReviewDto get(
            UUID caseId, IntakeEvidenceType evidenceType, UUID versionId
    ) {
        requireStaff();
        validateArguments(caseId, evidenceType, versionId);
        var authorized = assistedOriginations.authorizeRead(caseId);
        evidenceType.requireProduct(authorized.productCode());
        requireRelatedVersion(caseId, evidenceType, versionId);
        OcrResult result = requireCompletedResult(versionId);
        return toDto(evidenceType, result, reviews.findByOcrResultId(result.id()).orElse(null));
    }

    @Override
    @Transactional
    public IntakeOcrReviewDto finalizeReview(
            UUID caseId,
            IntakeEvidenceType evidenceType,
            UUID versionId,
            FinalizeIntakeOcrReviewRequest request
    ) {
        AuthenticatedUser actor = requireStaff();
        validateArguments(caseId, evidenceType, versionId);
        Objects.requireNonNull(request);
        var authorized = assistedOriginations.authorizeMutation(caseId);
        evidenceType.requireProduct(authorized.productCode());
        IntakeDocument document = requireRelatedVersionForUpdate(caseId, evidenceType, versionId);
        if (!versionId.equals(document.currentVersionId())) {
            throw new BusinessStateConflictException(
                    "OCR_REVIEW_REQUIRES_CURRENT_INTAKE_VERSION",
                    "Only the current intake evidence version may be reviewed."
            );
        }
        OcrResult discovered = requireCompletedResult(versionId);
        if (!discovered.id().equals(request.expectedOcrResultId())) {
            throw new BusinessStateConflictException(
                    "OCR_RESULT_CHANGED", "The expected OCR result is no longer current."
            );
        }
        Map<String, String> reviewedFields = validateReviewedFields(
                evidenceType, request.reviewedFields()
        );
        OcrResult result = results.findByIdForUpdate(discovered.id())
                .orElseThrow(IntakeOcrReviewService::resultNotFound);
        OcrReview existing = reviews.findByOcrResultId(result.id()).orElse(null);
        if (existing != null) {
            if (reviewedFields.equals(decryptReviewedFields(existing))) {
                return toDto(evidenceType, result, existing);
            }
            throw alreadyReviewed();
        }
        if (result.disposition() == OcrResultDisposition.REVIEWED) throw alreadyReviewed();

        LocalDateTime now = LocalDateTime.now(clock);
        UUID operationId = UUID.randomUUID();
        OcrReview review = reviews.save(new OcrReview(
                UUID.randomUUID(), result.id(), actor.userId(), result.disposition(),
                cipher.encrypt(writeJson(reviewedFields)), now
        ));
        OcrResult reviewedResult = results.save(result.reviewed());
        auditPublisher.publish(BusinessAuditEvent.single(
                BusinessOperationContext.user(operationId, actor.userId(), now),
                new BusinessAuditEntry(
                        BusinessAuditAction.OCR_RESULT_REVIEWED,
                        BusinessAuditEntityType.OCR_REVIEW,
                        review.id(),
                        BusinessAuditPayload.builder()
                                .put(BusinessAuditPayloadKey.OCR_REVIEW_ID, review.id())
                                .put(BusinessAuditPayloadKey.OCR_RESULT_ID, result.id())
                                .put(BusinessAuditPayloadKey.ASSISTED_ORIGINATION_CASE_ID, caseId)
                                .put(BusinessAuditPayloadKey.INTAKE_DOCUMENT_VERSION_ID, versionId)
                                .put(BusinessAuditPayloadKey.INTAKE_EVIDENCE_TYPE, evidenceType)
                                .build()
                )
        ));
        return toDto(evidenceType, reviewedResult, review);
    }

    private IntakeDocument requireRelatedVersion(
            UUID caseId, IntakeEvidenceType evidenceType, UUID versionId
    ) {
        IntakeDocument document = documents.findByCaseAndType(caseId, evidenceType)
                .orElseThrow(IntakeOcrReviewService::evidenceNotFound);
        documents.findVersionById(versionId)
                .filter(version -> version.intakeDocumentId().equals(document.id()))
                .orElseThrow(() -> new EntityNotFoundException(
                        "INTAKE_EVIDENCE_VERSION_NOT_FOUND", "Intake evidence version was not found."
                ));
        return document;
    }

    private IntakeDocument requireRelatedVersionForUpdate(
            UUID caseId, IntakeEvidenceType evidenceType, UUID versionId
    ) {
        IntakeDocument document = documents.findByCaseAndTypeForUpdate(caseId, evidenceType)
                .orElseThrow(IntakeOcrReviewService::evidenceNotFound);
        documents.findVersionById(versionId)
                .filter(version -> version.intakeDocumentId().equals(document.id()))
                .orElseThrow(() -> new EntityNotFoundException(
                        "INTAKE_EVIDENCE_VERSION_NOT_FOUND", "Intake evidence version was not found."
                ));
        return document;
    }

    private OcrResult requireCompletedResult(UUID versionId) {
        OcrJob job = jobs.findByIntakeDocumentVersionId(versionId)
                .orElseThrow(IntakeOcrReviewService::resultNotFound);
        if (job.state() != OcrJobState.COMPLETED) {
            throw new BusinessStateConflictException(
                    "OCR_RESULT_NOT_COMPLETED", "OCR processing has not completed."
            );
        }
        return results.findByJobId(job.id()).orElseThrow(IntakeOcrReviewService::resultNotFound);
    }

    private Map<String, String> validateReviewedFields(
            IntakeEvidenceType evidenceType, Map<String, String> submitted
    ) {
        if (submitted == null) {
            throw new BusinessRuleViolationException(
                    "OCR_REVIEW_FIELDS_REQUIRED", "Reviewed fields are required."
            );
        }
        TreeMap<String, String> canonical = new TreeMap<>();
        int payloadCharacters = 0;
        for (Map.Entry<String, String> entry : submitted.entrySet()) {
            if (!evidenceType.reviewableFields().contains(entry.getKey())) {
                throw new BusinessRuleViolationException(
                        "OCR_REVIEW_FIELD_NOT_ALLOWED", "A reviewed field is not allowed for this evidence type."
                );
            }
            if (entry.getValue() == null || entry.getValue().length() > 1000) {
                throw new BusinessRuleViolationException(
                        "OCR_REVIEW_FIELD_INVALID", "A reviewed field value is invalid."
                );
            }
            payloadCharacters += entry.getKey().length() + entry.getValue().length();
            canonical.put(entry.getKey(), entry.getValue());
        }
        if (payloadCharacters > MAX_REVIEWED_PAYLOAD_CHARS) {
            throw new BusinessRuleViolationException(
                    "OCR_REVIEW_PAYLOAD_TOO_LARGE", "The OCR review payload is too large."
            );
        }
        return Collections.unmodifiableMap(canonical);
    }

    private IntakeOcrReviewDto toDto(
            IntakeEvidenceType evidenceType, OcrResult result, OcrReview review
    ) {
        List<IntakeOcrFieldSuggestionDto> suggestions = decryptSuggestions(result).stream()
                .filter(item -> evidenceType.reviewableFields().contains(item.fieldName()))
                .filter(item -> item.proposedValue() != null && item.proposedValue().length() <= 1000)
                .map(item -> new IntakeOcrFieldSuggestionDto(
                        item.fieldName(), item.proposedValue(), boundedConfidence(item.confidence())
                ))
                .limit(evidenceType.reviewableFields().size())
                .toList();
        Map<String, String> reviewedFields = review == null
                ? Map.of() : decryptReviewedFields(review);
        return new IntakeOcrReviewDto(
                result.id(), evidenceType.name(), result.disposition().name(),
                suggestions, reviewedFields, review == null ? null : review.reviewedAt()
        );
    }

    private List<StoredSuggestion> decryptSuggestions(OcrResult result) {
        try {
            return objectMapper.readValue(
                    cipher.decrypt(result.encryptedStructuredSuggestions()),
                    new TypeReference<ArrayList<StoredSuggestion>>() { }
            );
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw unavailable(exception);
        }
    }

    private Map<String, String> decryptReviewedFields(OcrReview review) {
        try {
            Map<String, String> fields = objectMapper.readValue(
                    cipher.decrypt(review.encryptedReviewedFields()),
                    new TypeReference<TreeMap<String, String>>() { }
            );
            return Collections.unmodifiableMap(fields);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw unavailable(exception);
        }
    }

    private String writeJson(Map<String, String> fields) {
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (JsonProcessingException exception) {
            throw unavailable(exception);
        }
    }

    private AuthenticatedUser requireStaff() {
        AuthenticatedUser actor = currentUsers.currentUser();
        if (!"STAFF".equals(actor.userType()) || actor.optionalCustomerId().isPresent()
                || !actor.hasPermission(PERMISSION)) {
            throw new AuthorizationException(
                    "INTAKE_OCR_REVIEW_ACCESS_DENIED", "Staff intake OCR review access is denied."
            );
        }
        return actor;
    }

    private static void validateArguments(
            UUID caseId, IntakeEvidenceType evidenceType, UUID versionId
    ) {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(evidenceType);
        Objects.requireNonNull(versionId);
    }

    private static EntityNotFoundException evidenceNotFound() {
        return new EntityNotFoundException(
                "INTAKE_EVIDENCE_NOT_FOUND", "Intake evidence was not found."
        );
    }

    private static EntityNotFoundException resultNotFound() {
        return new EntityNotFoundException("OCR_RESULT_NOT_FOUND", "OCR result was not found.");
    }

    private static BusinessStateConflictException alreadyReviewed() {
        return new BusinessStateConflictException(
                "OCR_RESULT_ALREADY_REVIEWED", "The OCR result already has a final review."
        );
    }

    private static ServiceUnavailableException unavailable(Exception cause) {
        ServiceUnavailableException exception = new ServiceUnavailableException(
                "OCR_REVIEW_UNAVAILABLE", "OCR review is temporarily unavailable."
        );
        exception.initCause(cause);
        return exception;
    }

    private static BigDecimal boundedConfidence(BigDecimal confidence) {
        if (confidence == null || confidence.compareTo(BigDecimal.ZERO) < 0
                || confidence.compareTo(BigDecimal.ONE) > 0) {
            return null;
        }
        return confidence;
    }

    private record StoredSuggestion(
            String fieldName, String proposedValue, BigDecimal confidence
    ) {
    }
}
