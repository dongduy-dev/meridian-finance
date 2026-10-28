package com.meridian.platform;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.meridian.platform.document.application.port.in.DocumentChecklistUseCase;
import com.meridian.platform.document.application.port.in.LoanIntakeEvidenceUseCase;
import com.meridian.platform.document.application.port.in.AssistedActionEvidenceUseCase;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

class ArchitectureRulesTest {

    private static final Set<String> FEATURES = Set.of(
            "identity", "customer", "partner", "loan", "approval", "document", "audit", "notification");

    private static final Set<String> PUBLISHED_APPLICATION_SUPPORT = Set.of(
            "com.meridian.platform.partner.application.dto.CustomerPartnerEmployeeEligibilityDto",
            "com.meridian.platform.partner.application.dto.CustomerPartnerEmployeeEligibilityDto$Status",
            "com.meridian.platform.partner.application.dto.CustomerPartnerEmployeeLinkSnapshotDto",
            "com.meridian.platform.customer.application.port.in.SensitiveContractBankAccount");

    private static String feature(JavaClass type) {
        while (type.isArray()) type = type.getComponentType();
        String name = type.getPackageName();
        String root = "com.meridian.platform.";
        if (!name.startsWith(root)) return null;
        name = name.substring(root.length());
        if (name.startsWith("testsupport.architecture.")) {
            name = name.substring("testsupport.architecture.".length());
        }
        String owner = name.split("\\.")[0];
        return FEATURES.contains(owner) ? owner : null;
    }

    private static ArchRule contextIsolation(String layer) {
        return classes().that().resideInAPackage(".." + layer + "..")
                .should(new ArchCondition<>("keep foreign internal vocabulary behind infrastructure boundaries") {
                    @Override
                    public void check(JavaClass origin, ConditionEvents events) {
                        String owner = feature(origin);
                        if (owner == null) return;
                        origin.getDirectDependenciesFromSelf().forEach(dependency -> {
                            JavaClass target = dependency.getTargetClass();
                            String provider = feature(target);
                            String name = target.getPackageName();
                            boolean internal = name.contains(".domain.")
                                    || name.contains(".application.service")
                                    || name.contains(".application.mapper")
                                    || name.contains(".application.dto")
                                    || name.contains(".application.port.out");
                            if (provider != null && !owner.equals(provider)
                                    && (layer.equals("domain")
                                    || (internal && !PUBLISHED_APPLICATION_SUPPORT.contains(baseTypeName(target))))) {
                                events.add(SimpleConditionEvent.violated(origin, dependency.getDescription()));
                            }
                        });
                    }
                });
    }

    private static final String ROOT = "com.meridian.platform.";
    private static final java.util.Map<String, String> APPROVAL_SPI_BINDINGS = java.util.Map.of(
            ROOT + "approval.application.spi.loan.ApprovalLoanCaseSpi",
            ROOT + "loan.infrastructure.adapter.in.approval.ApprovalLoanCaseAdapter",
            ROOT + "approval.application.spi.loan.ApprovalLoanReviewCycleSpi",
            ROOT + "loan.infrastructure.adapter.in.approval.ApprovalLoanReviewCycleAdapter",
            ROOT + "approval.application.spi.loan.ApprovalLoanReviewHistorySpi",
            ROOT + "loan.infrastructure.adapter.in.approval.ApprovalLoanReviewHistoryAdapter");

    private static final Set<String> APPROVAL_SPI_TYPES = Set.of(
            ROOT + "approval.application.spi.loan.ApprovalLoanCaseSpi",
            ROOT + "approval.application.spi.loan.ApprovalLoanCaseSpi$CaseSnapshot",
            ROOT + "approval.application.spi.loan.ApprovalLoanCaseSpi$DocumentReadinessSnapshot",
            ROOT + "approval.application.spi.loan.ApprovalLoanCaseSpi$ProductReadinessSnapshot",
            ROOT + "approval.application.spi.loan.ApprovalLoanCaseSpi$ReviewCycleSnapshot",
            ROOT + "approval.application.spi.loan.ApprovalLoanCaseSpi$CorrectionOptionSnapshot",
            ROOT + "approval.application.spi.loan.ApprovalLoanCaseSpi$QueuePageSnapshot",
            ROOT + "approval.application.spi.loan.ApprovalLoanCaseSpi$QueueItemSnapshot",
            ROOT + "approval.application.spi.loan.ApprovalLoanReviewCycleSpi",
            ROOT + "approval.application.spi.loan.ApprovalLoanReviewCycleSpi$ActiveReviewCycleSnapshot",
            ROOT + "approval.application.spi.loan.ApprovalLoanReviewHistorySpi",
            ROOT + "approval.application.spi.loan.ApprovalLoanReviewHistorySpi$HistorySnapshot");

    private static final Set<String> LOAN_APPROVAL_INPUT_TYPES = APPROVAL_SPI_TYPES.stream()
            .map(name -> name.replace("approval.application.spi.loan.ApprovalLoan", "loan.application.port.in.QueryApprovalLoan")
                    .replace("Spi", "UseCase"))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    private static ArchRule approvalSpiClosure(Set<String> published) {
        return classes().that().resideInAPackage("..approval.application.spi.loan..")
                .should(new ArchCondition<>("be explicitly published SPI interfaces and immutable support records") {
                    @Override
                    public void check(JavaClass type, ConditionEvents events) {
                        if (!published.contains(type.getName())) {
                            events.add(SimpleConditionEvent.violated(type, "Unpublished SPI type " + type.getName()));
                        }
                        if (!type.isInterface() && !type.isRecord()) {
                            events.add(SimpleConditionEvent.violated(type, "SPI support must be a record: " + type.getName()));
                        }
                    }
                }).andShould().onlyDependOnClassesThat(new DescribedPredicate<>("are in the exact SPI support closure") {
                    @Override
                    public boolean test(JavaClass type) {
                        String name = baseTypeName(type);
                        return name.startsWith("java.") || published.contains(name);
                    }
                });
    }

    private static final ArchRule APPROVAL_SPI_IMPLEMENTERS = classes()
            .should(new ArchCondition<>("implement Approval SPIs only in designated Loan inbound adapters") {
                @Override
                public void check(JavaClass type, ConditionEvents events) {
                    if (type.isInterface()) return;
                    APPROVAL_SPI_BINDINGS.forEach((spi, adapter) -> {
                        if (type.isAssignableTo(spi) && !type.getName().equals(adapter)) {
                            events.add(SimpleConditionEvent.violated(type, type.getName() + " illegally implements " + spi));
                        }
                    });
                }
            });

    private static final ArchRule LOAN_HAS_NO_APPLICATION_SPI_DEPENDENCY = noClasses()
            .that().resideInAnyPackage("..loan.application..", "..loan.domain..")
            .should().dependOnClassesThat().resideInAPackage("..approval.application.spi..")
            .allowEmptyShould(true);

    private static final ArchRule THIN_APPROVAL_SPI_ADAPTERS = classes()
            .that(new DescribedPredicate<>("implement the three Approval SPIs") {
                @Override
                public boolean test(JavaClass type) {
                    return !type.isInterface() && APPROVAL_SPI_BINDINGS.keySet().stream().anyMatch(type::isAssignableTo);
                }
            }).should().onlyDependOnClassesThat(new DescribedPredicate<>("are approved SPI facts, own query inputs, or component wiring") {
                @Override
                public boolean test(JavaClass type) {
                    String name = baseTypeName(type);
                    return name.startsWith("java.") || APPROVAL_SPI_TYPES.contains(name)
                            || LOAN_APPROVAL_INPUT_TYPES.contains(name) || APPROVAL_SPI_BINDINGS.containsValue(name)
                            || name.equals("org.springframework.stereotype.Component");
                }
            });

    @Test
    void approvalSpisHaveExplicitClosureAndDesignatedThinInboundImplementers() {
        approvalSpiClosure(APPROVAL_SPI_TYPES).check(importedClasses);
        APPROVAL_SPI_IMPLEMENTERS.check(importedClasses);
        THIN_APPROVAL_SPI_ADAPTERS.check(importedClasses);
        LOAN_HAS_NO_APPLICATION_SPI_DEPENDENCY.check(importedClasses);
        classes().that(new DescribedPredicate<>("are Loan-owned Approval query inputs and results") {
            @Override
            public boolean test(JavaClass type) { return LOAN_APPROVAL_INPUT_TYPES.contains(type.getName()); }
        }).should().onlyDependOnClassesThat(new DescribedPredicate<>("are JDK or the exact Loan input closure") {
            @Override
            public boolean test(JavaClass type) {
                return baseTypeName(type).startsWith("java.") || LOAN_APPROVAL_INPUT_TYPES.contains(baseTypeName(type));
            }
        }).check(importedClasses);
        noClasses().that().resideInAPackage("com.meridian.platform.approval..")
                .should().dependOnClassesThat().resideInAPackage("com.meridian.platform.loan..")
                .check(importedClasses);
        for (String retired : Set.of("ApprovalLoanCasePort", "ApprovalLoanReviewCyclePort", "ApprovalLoanReviewHistoryPort")) {
            assertTrue(importedClasses.stream().noneMatch(type -> type.getSimpleName().equals(retired)), retired);
        }
    }

    @Test
    void approvalSpiRulesRejectWrongImplementerApplicationDependencyAndRepositoryAccess() {
        assertRuleRejects(APPROVAL_SPI_IMPLEMENTERS,
                com.meridian.platform.testsupport.architecture.customer.infrastructure.WrongApprovalSpi.class,
                "ApprovalLoanReviewCycleSpi");
        assertRuleRejects(LOAN_HAS_NO_APPLICATION_SPI_DEPENDENCY,
                com.meridian.platform.testsupport.architecture.loan.application.ForeignApprovalSpi.class,
                "ApprovalLoanReviewCycleSpi");
        assertRuleRejects(THIN_APPROVAL_SPI_ADAPTERS,
                com.meridian.platform.testsupport.architecture.loan.infrastructure.adapter.in.approval.RepositoryLeak.class,
                "LoanReviewCycleRepository");
    }

    @Test
    void approvalSpiClosureRejectsNestedGenericInternalTypeLeaks() {
        Class<?> fixture = com.meridian.platform.testsupport.architecture.approval.application.spi.loan.InternalTypeLeak.class;
        var nested = fixture.getDeclaredClasses()[0];
        var published = new java.util.HashSet<>(APPROVAL_SPI_TYPES);
        published.add(fixture.getName());
        published.add(nested.getName());
        var result = approvalSpiClosure(published).evaluate(new ClassFileImporter().importClasses(fixture, nested));
        assertTrue(result.hasViolation());
        assertTrue(result.getFailureReport().toString().contains("LoanApplication"));
        assertRuleRejects(approvalSpiClosure(APPROVAL_SPI_TYPES), fixture, "Unpublished SPI type");
    }

    private static final Set<String> APPROVAL_EVENT_TYPES = Set.of(
            "com.meridian.platform.approval.application.event.ApprovalDecisionRecordedEvent",
            "com.meridian.platform.approval.application.event.ReviewRecommendationRecordedEvent",
            "com.meridian.platform.approval.application.event.ApprovalDecisionEventAction",
            "com.meridian.platform.approval.application.event.ReviewRecommendationEventAction",
            "com.meridian.platform.approval.application.event.CorrectionPlanFacts",
            "com.meridian.platform.approval.application.event.CorrectionTaskFacts");

    private static final ArchRule APPROVAL_EVENT_CLOSURE = classes()
            .that().resideInAPackage("..approval.application.event..")
            .should().onlyDependOnClassesThat(new DescribedPredicate<>("are immutable published facts or technical envelope types") {
                @Override
                public boolean test(JavaClass type) {
                    String name = baseTypeName(type);
                    return name.startsWith("java.") || APPROVAL_EVENT_TYPES.contains(name)
                            || name.equals("com.meridian.platform.shared.application.operation.BusinessOperationContext");
                }
            });

    private static final ArchRule PRIVATE_APPROVAL_CORRECTION_POLICY = noClasses()
            .that().resideOutsideOfPackage("com.meridian.platform.approval..")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("com.meridian.platform.approval.application.service.CorrectionPlanPolicy");

    private static final ArchRule PRIVATE_LOAN_CORRECTION_POLICY = noClasses()
            .that().resideOutsideOfPackage("com.meridian.platform.loan..")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("com.meridian.platform.loan.application.service.LoanCorrectionPlanPolicy");

    @Test
    void domainAndApplicationKeepForeignInternalVocabularyIsolated() {
        contextIsolation("domain").check(importedClasses);
        contextIsolation("application").check(importedClasses);
        PRIVATE_APPROVAL_CORRECTION_POLICY.check(importedClasses);
        PRIVATE_LOAN_CORRECTION_POLICY.check(importedClasses);
    }

    @Test
    void publishedApprovalEventsExposeOnlyTheExplicitFactClosure() {
        APPROVAL_EVENT_CLOSURE.check(importedClasses);
    }

    @Test
    void contextIsolationRejectsForeignDomainAndPolicyFixtures() {
        assertRuleRejects(contextIsolation("domain"),
                com.meridian.platform.testsupport.architecture.loan.domain.ForeignCorrectionReason.class,
                "CorrectionReasonCode");
        Class<?> policy = com.meridian.platform.testsupport.architecture.loan.application.ForeignCorrectionPolicy.class;
        assertRuleRejects(contextIsolation("application"), policy, "CorrectionPlanPolicy");
        assertRuleRejects(PRIVATE_APPROVAL_CORRECTION_POLICY, policy, "CorrectionPlanPolicy");
        assertRuleRejects(contextIsolation("application"),
                com.meridian.platform.testsupport.architecture.loan.application.ForeignCorrectionReason.class,
                "CorrectionReasonCode");
        assertRuleRejects(PRIVATE_LOAN_CORRECTION_POLICY,
                com.meridian.platform.testsupport.architecture.document.infrastructure.ForeignLoanCorrectionPolicy.class,
                "LoanCorrectionPlanPolicy");
    }

    @Test
    void existingPublishedPartnerFactsAndClearableCustomerCarrierRemainLegal() {
        contextIsolation("application").check(new ClassFileImporter().importClasses(
                com.meridian.platform.testsupport.architecture.loan.application.PublishedSupport.class));
    }

    @Test
    void publishedEventClosureRejectsNestedRequestAndDomainEnumLeaks() {
        assertRuleRejects(APPROVAL_EVENT_CLOSURE,
                com.meridian.platform.testsupport.architecture.approval.application.event.RequestLeak.class,
                "CorrectionPlanRequest");
        assertRuleRejects(APPROVAL_EVENT_CLOSURE,
                com.meridian.platform.testsupport.architecture.approval.application.event.DomainEnumLeak.class,
                "CorrectionReasonCode");
    }

    private void assertRuleRejects(ArchRule rule, Class<?> fixture, String leakedType) {
        var result = rule.evaluate(new ClassFileImporter().importClasses(fixture));
        assertTrue(result.hasViolation());
        assertTrue(result.getFailureReport().toString().contains(leakedType));
    }

    private static final JavaClasses importedClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.meridian.platform");

    private static final ArchRule INBOUND_CONTRACTS_MUST_NOT_USE_OUTPUT_PORTS = noClasses()
            .that().resideInAnyPackage("..infrastructure.adapter.in.web..", "..application.port.in..")
            .should().dependOnClassesThat().resideInAPackage("..application.port.out..");

    private static final Set<String> DOCUMENT_PROVIDER_TYPES = Set.of(
            DocumentChecklistUseCase.class.getName(),
            DocumentChecklistUseCase.SubmissionProduct.class.getName(),
            DocumentChecklistUseCase.SubmissionChecklistInitialState.class.getName(),
            DocumentChecklistUseCase.SubmissionEvidenceRequirement.class.getName(),
            DocumentChecklistUseCase.SubmissionChecklistSnapshot.class.getName(),
            DocumentChecklistUseCase.SubmissionChecklistItemSnapshot.class.getName(),
            DocumentChecklistUseCase.ChecklistReadinessSnapshot.class.getName(),
            DocumentChecklistUseCase.CurrentDocumentVersionSnapshot.class.getName(),
            DocumentChecklistUseCase.CurrentDocumentVersionTargetSnapshot.class.getName(),
            LoanIntakeEvidenceUseCase.class.getName(),
            AssistedActionEvidenceUseCase.class.getName(),
            AssistedActionEvidenceUseCase.EvidenceSnapshot.class.getName());

    private static final Set<String> LOAN_DOCUMENT_OUTPUTS = Set.of(
            "com.meridian.platform.loan.application.port.out.LoanDocumentChecklistPort",
            "com.meridian.platform.loan.application.port.out.LoanIntakeEvidencePort",
            "com.meridian.platform.loan.application.port.out.LoanAssistedActionEvidencePort");

    private static boolean publishedType(String name, Set<String> roots) {
        return roots.stream().anyMatch(root -> name.equals(root) || name.startsWith(root + "$"));
    }

    private static String baseTypeName(JavaClass type) {
        while (type.isArray()) type = type.getComponentType();
        return type.getName();
    }

    private static ArchRule documentInputClosure(Set<String> inputs) {
        return classes().that(new DescribedPredicate<>("are the published Document evidence/checklist inputs and support records") {
            @Override
            public boolean test(JavaClass type) {
                return inputs.contains(type.getName());
            }
        }).should().onlyDependOnClassesThat(new DescribedPredicate<>("belong to the explicit public contract closure") {
            @Override
            public boolean test(JavaClass type) {
                String name = baseTypeName(type);
                return name.startsWith("java.") || inputs.contains(name)
                        || name.equals("com.meridian.platform.shared.application.operation.BusinessOperationContext");
            }
        });
    }

    private static final ArchRule ORDINARY_DOCUMENT_ADAPTERS = classes().that(new DescribedPredicate<>("implement Loan's private Document output ports") {
        @Override
        public boolean test(JavaClass type) {
            return !type.isInterface() && LOAN_DOCUMENT_OUTPUTS.stream().anyMatch(type::isAssignableTo);
        }
    }).should().resideInAPackage("com.meridian.platform.loan.infrastructure.adapter.out.document")
            .andShould().onlyDependOnClassesThat(new DescribedPredicate<>("are own port facts or approved Document public inputs") {
                @Override
                public boolean test(JavaClass type) {
                    String name = baseTypeName(type);
                    return name.startsWith("java.") || publishedType(name, LOAN_DOCUMENT_OUTPUTS)
                            || DOCUMENT_PROVIDER_TYPES.contains(name)
                            || name.equals("com.meridian.platform.loan.domain.model.LoanDocumentType")
                            || name.equals("com.meridian.platform.loan.domain.model.ProductCode")
                            || name.equals("com.meridian.platform.shared.application.operation.BusinessOperationContext")
                            || name.equals("org.springframework.stereotype.Component")
                            || name.equals("com.meridian.platform.loan.infrastructure.adapter.out.document.LoanDocumentChecklistAdapter")
                            || name.equals("com.meridian.platform.loan.infrastructure.adapter.out.document.LoanIntakeEvidenceAdapter")
                            || name.equals("com.meridian.platform.loan.infrastructure.adapter.out.document.LoanAssistedActionEvidenceAdapter");
                }
            });

    @Test
    void documentApplicationMustNotDependOnLoanOutputPorts() {
        noClasses().that().resideInAPackage("..document.application..")
                .should().dependOnClassesThat().resideInAPackage("com.meridian.platform.loan.application.port.out..")
                .check(importedClasses);
    }

    @Test
    void publishedDocumentInputClosureContainsNoInternalTypes() {
        documentInputClosure(DOCUMENT_PROVIDER_TYPES).check(importedClasses);
    }

    @Test
    void ordinaryLoanDocumentBindingsUseOnlyPublishedInputsAndOwnFacts() {
        ORDINARY_DOCUMENT_ADAPTERS.check(importedClasses);
    }

    @Test
    void publicContractRuleRejectsGenericNestedInternalTypeLeak() {
        Class<?> fixture = com.meridian.platform.testsupport.architecture.document.application.port.in.InternalTypeLeak.class;
        var imported = new ClassFileImporter().importClasses(fixture, fixture.getDeclaredClasses()[0]);
        var result = documentInputClosure(Set.of(fixture.getName(), fixture.getDeclaredClasses()[0].getName())).evaluate(imported);
        assertTrue(result.hasViolation());
        assertTrue(result.getFailureReport().toString().contains("LoanApplication"));
    }

    @Test
    void ordinaryAdapterRuleRejectsDirectProviderRepositoryAccess() {
        var imported = new ClassFileImporter().importClasses(
                com.meridian.platform.testsupport.architecture.loan.infrastructure.adapter.out.document.RepositoryLeak.class);
        var result = ORDINARY_DOCUMENT_ADAPTERS.evaluate(imported);
        assertTrue(result.hasViolation());
        assertTrue(result.getFailureReport().toString().contains("IntakeDocumentRepository"));
    }

    @Test
    void webAdaptersAndInputPortsMustNotDependOnOutputPorts() {
        INBOUND_CONTRACTS_MUST_NOT_USE_OUTPUT_PORTS.check(importedClasses);
    }

    @Test
    void webOutputPortRuleRejectsAHelperSignatureLeak() {
        assertOutputPortSignatureRejected(
                com.meridian.platform.testsupport.architecture.customer.infrastructure.adapter.in.web.OutputPortSignatureLeak.class);
    }

    @Test
    void inputPortRuleRejectsAReturnTypeLeak() {
        assertOutputPortSignatureRejected(
                com.meridian.platform.testsupport.architecture.customer.application.port.in.OutputPortSignatureLeak.class);
    }

    private void assertOutputPortSignatureRejected(Class<?> fixture) {
        var result = INBOUND_CONTRACTS_MUST_NOT_USE_OUTPUT_PORTS.evaluate(new ClassFileImporter().importClasses(fixture));
        assertTrue(result.hasViolation());
        assertTrue(result.getFailureReport().toString().contains("CustomerIdentityEvidencePort$Content"));
    }

    @Test
    void domainMustNotDependOnSpring() {
        noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework..")
                .check(importedClasses);
    }

    @Test
    void domainMustNotDependOnJpaApplicationOrInfrastructure() {
        noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("jakarta.persistence..", "..application..", "..infrastructure..")
                .check(importedClasses);
    }

    @Test
    void domainAndApplicationMustNotDependOnSecurityImplementation() {
        noClasses()
                .that()
                .resideInAnyPackage("..domain..", "..application..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework.security..",
                        "io.jsonwebtoken..",
                        "com.auth0.jwt..",
                        "com.meridian.platform.identity.infrastructure.security.."
                )
                .check(importedClasses);
    }

    @Test
    void applicationMustNotDependOnInfrastructure() {
        noClasses()
                .that()
                .resideInAPackage("..application..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..infrastructure..")
                .check(importedClasses);
    }

    @Test
    void sharedMustNotDependOnFeatureModules() {
        noClasses()
                .that()
                .resideInAPackage("com.meridian.platform.shared..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.meridian.platform.identity..",
                        "com.meridian.platform.customer..",
                        "com.meridian.platform.partner..",
                        "com.meridian.platform.loan..",
                        "com.meridian.platform.approval..",
                        "com.meridian.platform.document..",
                        "com.meridian.platform.audit..",
                        "com.meridian.platform.notification.."
                )
                .check(importedClasses);
    }

    @Test
    void identityApplicationAndDomainMustUsePortsForCustomerAndNotification() {
        noClasses()
                .that()
                .resideInAnyPackage(
                        "com.meridian.platform.identity.application..",
                        "com.meridian.platform.identity.domain.."
                )
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.meridian.platform.customer..",
                        "com.meridian.platform.notification..",
                        "org.springframework.mail..",
                        "jakarta.mail.."
                )
                .check(importedClasses);
    }

    @Test
    void notificationApplicationMustNotDependOnMailTransport() {
        noClasses()
                .that()
                .resideInAPackage("com.meridian.platform.notification.application..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework.mail..", "jakarta.mail..")
                .check(importedClasses);
    }

    // Exact publication, including support types. Package placement never implies publication.
    private static final Set<String> DOCUMENT_SPI_TYPES = Set.of(
            ROOT + "document.application.spi.loan.LoanDocumentWorkflowSpi",
            ROOT + "document.application.spi.loan.LoanDocumentWorkflowSpi$LoanDocumentWorkflowSnapshot",
            ROOT + "document.application.spi.loan.LoanDocumentCorrectionSpi",
            ROOT + "document.application.spi.loan.LoanDocumentCorrectionSpi$StaffUploadAuthority",
            ROOT + "document.application.spi.loan.LoanDocumentReviewCorrectionSpi",
            ROOT + "document.application.spi.loan.LoanAssistedOriginationSpi",
            ROOT + "document.application.spi.loan.LoanAssistedOriginationSpi$AuthorizedIntake",
            ROOT + "document.application.spi.loan.LoanAssistedActionAuthorizationSpi",
            ROOT + "document.application.spi.identity.DocumentStaffActorDirectorySpi",
            ROOT + "document.application.spi.identity.DocumentStaffActorDirectorySpi$StaffActorSummary");

    private static final java.util.Map<String, String> SPI_BINDINGS = allSpiBindings();

    private static java.util.Map<String, String> allSpiBindings() {
        var bindings = new java.util.LinkedHashMap<>(APPROVAL_SPI_BINDINGS);
        bindings.put(ROOT + "document.application.spi.loan.LoanDocumentWorkflowSpi", ROOT + "loan.infrastructure.adapter.in.document.LoanDocumentWorkflowAdapter");
        bindings.put(ROOT + "document.application.spi.loan.LoanDocumentCorrectionSpi", ROOT + "loan.infrastructure.adapter.in.document.LoanDocumentCorrectionAdapter");
        bindings.put(ROOT + "document.application.spi.loan.LoanDocumentReviewCorrectionSpi", ROOT + "loan.infrastructure.adapter.in.document.LoanDocumentReviewCorrectionAdapter");
        bindings.put(ROOT + "document.application.spi.loan.LoanAssistedOriginationSpi", ROOT + "loan.infrastructure.adapter.in.document.AssistedOriginationEvidenceAuthorizationAdapter");
        bindings.put(ROOT + "document.application.spi.loan.LoanAssistedActionAuthorizationSpi", ROOT + "loan.infrastructure.adapter.in.document.AssistedActionEvidenceAuthorizationAdapter");
        bindings.put(ROOT + "document.application.spi.identity.DocumentStaffActorDirectorySpi", ROOT + "identity.infrastructure.adapter.in.document.DocumentStaffActorDirectoryAdapter");
        return java.util.Map.copyOf(bindings);
    }

    private static final Set<String> S5_PROVIDER_INPUT_TYPES = Set.of(
            ROOT + "loan.application.port.in.LoanDocumentWorkflowUseCase",
            ROOT + "loan.application.port.in.LoanDocumentWorkflowUseCase$LoanDocumentWorkflowSnapshot",
            ROOT + "loan.domain.model.LoanApplicationStatus",
            ROOT + "loan.domain.model.OriginationChannel",
            ROOT + "loan.application.port.in.AuthorizeDocumentCorrectionUploadUseCase",
            ROOT + "loan.application.port.in.AuthorizeDocumentCorrectionUploadUseCase$StaffUploadAuthority",
            ROOT + "loan.application.port.in.RequestPreReviewDocumentCorrectionUseCase",
            ROOT + "loan.domain.model.LoanCorrectionReasonCode",
            ROOT + "loan.application.port.in.AuthorizeAssistedOriginationEvidenceUseCase",
            ROOT + "loan.application.port.in.AuthorizeAssistedOriginationEvidenceUseCase$AuthorizedAssistedOrigination",
            ROOT + "loan.application.port.in.AuthorizeAssistedActionEvidenceUseCase",
            ROOT + "identity.application.port.in.QueryStaffActorSummariesUseCase",
            ROOT + "identity.application.port.in.StaffActorSummary");

    private static final Set<String> ORDINARY_PUBLIC_TYPES = Set.of(
            ROOT + "approval.application.port.in.QueryApprovedOfferApprovalProvenanceUseCase",
            ROOT + "approval.application.port.in.ApprovedOfferApprovalProvenance",
            ROOT + "customer.application.port.in.ContractBankAccountUseCase",
            ROOT + "customer.application.port.in.ContractBankAccountUseCase$ContractBankAccountState",
            ROOT + "customer.application.port.in.QueryAccountingCustomerIdentityUseCase",
            ROOT + "customer.application.port.in.AccountingCustomerIdentity",
            ROOT + "customer.application.port.in.QueryApprovalCustomerIdentityUseCase",
            ROOT + "customer.application.port.in.ApprovalCustomerIdentity",
            ROOT + "customer.application.port.in.QueryCustomerIdentityEvidenceUseCase",
            ROOT + "customer.application.port.in.CustomerIdentityEvidenceSnapshot",
            ROOT + "customer.application.port.in.QueryCustomerLoanCaseContactUseCase",
            ROOT + "customer.application.port.in.CustomerLoanCaseContact",
            ROOT + "customer.application.port.in.QueryCustomerReadinessUseCase",
            ROOT + "customer.application.port.in.CustomerReadinessSnapshot",
            ROOT + "customer.application.port.in.QueryServicingCustomerContextUseCase",
            ROOT + "customer.application.port.in.QueryServicingCustomerContextUseCase$BusinessIdentity",
            ROOT + "customer.application.port.in.QueryServicingCustomerContextUseCase$CurrentContact",
            ROOT + "customer.application.port.in.RegisterCustomerUseCase",
            ROOT + "customer.application.port.in.RegisteredCustomer",
            ROOT + "customer.application.port.in.RevealCustomerIdentityReferenceUseCase",
            ROOT + "customer.application.port.in.RevealCustomerIdentityReferenceUseCase$Command",
            ROOT + "customer.application.port.in.RevealCustomerIdentityReferenceUseCase$Result",
            ROOT + "customer.application.port.in.VerifyCustomerDigitalAccessUseCase",
            ROOT + "customer.application.port.in.CustomerDigitalAccessCandidate",
            ROOT + "document.application.port.in.CustomerIdentityEvidenceUseCase",
            ROOT + "document.application.port.in.CustomerIdentityEvidenceUseCase$Evidence",
            ROOT + "document.application.port.in.CustomerIdentityEvidenceUseCase$Content",
            ROOT + "identity.application.port.in.QueryStaffActorSummariesUseCase",
            ROOT + "identity.application.port.in.StaffActorSummary",
            ROOT + "identity.application.port.in.QueryWorkflowActorSummariesUseCase",
            ROOT + "identity.application.port.in.WorkflowActorSummary",
            ROOT + "notification.application.port.in.CustomerDigitalAccessInvitationMessage",
            ROOT + "notification.application.port.in.EmailVerificationMessage",
            ROOT + "notification.application.port.in.PasswordResetMessage",
            ROOT + "notification.application.port.in.StaffPasswordSetupMessage",
            ROOT + "notification.application.port.in.SendCustomerDigitalAccessInvitationUseCase",
            ROOT + "notification.application.port.in.SendEmailVerificationUseCase",
            ROOT + "notification.application.port.in.SendPasswordResetUseCase",
            ROOT + "notification.application.port.in.SendStaffPasswordSetupUseCase",
            ROOT + "partner.application.port.in.QueryCustomerPartnerEmployeeLinkUseCase",
            ROOT + "document.application.event.DocumentUploadsCompletedEvent");

    private static final Set<String> PUBLIC_TYPES = java.util.stream.Stream.of(
            APPROVAL_SPI_TYPES, DOCUMENT_SPI_TYPES, DOCUMENT_PROVIDER_TYPES, PUBLISHED_APPLICATION_SUPPORT,
            ORDINARY_PUBLIC_TYPES, APPROVAL_EVENT_TYPES).flatMap(Set::stream)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    private static boolean technical(JavaClass type) {
        String name = baseTypeName(type);
        return jdk(type)
                || name.equals(ROOT + "shared.application.operation.BusinessOperationContext");
    }

    private static boolean jdk(JavaClass type) {
        while (type.isArray()) type = type.getComponentType();
        return type.isPrimitive() || type.getName().startsWith("java.");
    }

    private static ArchRule publishedClosure(Set<String> published) {
        return classes().that(new DescribedPredicate<>("are explicitly published contracts or support types") {
            @Override public boolean test(JavaClass type) { return published.contains(type.getName()); }
        }).should().onlyDependOnClassesThat(new DescribedPredicate<>("are exact published support or approved technical types") {
            @Override public boolean test(JavaClass type) { return technical(type) || published.contains(baseTypeName(type)); }
        });
    }

    private static ArchRule publicBoundaries(Set<String> published) {
        return classes().should(new ArchCondition<>("use only exact publications across features") {
        @Override public void check(JavaClass origin, ConditionEvents events) {
            String owner = feature(origin);
            if (owner == null) return;
            origin.getDirectDependenciesFromSelf().forEach(dep -> {
                String provider = feature(dep.getTargetClass());
                if (provider != null && !owner.equals(provider) && !published.contains(baseTypeName(dep.getTargetClass()))) {
                    events.add(SimpleConditionEvent.violated(origin, "Unpublished foreign type: " + dep.getDescription()));
                }
            });
        }
    });
    }

    private static final ArchRule PUBLIC_BOUNDARIES = publicBoundaries(PUBLIC_TYPES);

    private static final ArchRule ALL_SPI_IMPLEMENTERS = classes().should(new ArchCondition<>("use only designated SPI implementers") {
        @Override public void check(JavaClass type, ConditionEvents events) {
            if (type.isInterface()) return;
            SPI_BINDINGS.forEach((spi, adapter) -> {
                if (type.isAssignableTo(spi) && !type.getName().equals(adapter)) {
                    events.add(SimpleConditionEvent.violated(type, type.getName() + " illegally implements " + spi));
                }
            });
        }
    });

    private static final ArchRule SPI_PROVIDER_ISOLATION = classes().that().resideInAnyPackage("..application..", "..domain..")
            .should(new ArchCondition<>("keep provider application and domain independent of foreign SPIs") {
                @Override public void check(JavaClass origin, ConditionEvents events) {
                    String owner = feature(origin);
                    origin.getDirectDependenciesFromSelf().forEach(dep -> {
                        String provider = feature(dep.getTargetClass());
                        if (owner != null && provider != null && !owner.equals(provider)
                                && dep.getTargetClass().getPackageName().contains(".application.spi.")) {
                            events.add(SimpleConditionEvent.violated(origin, dep.getDescription()));
                        }
                    });
                }
            });

    private static ArchRule providerInputOwnership(Set<String> inputTypes) {
        return classes().that(new DescribedPredicate<>("are provider inputs or support types") {
            @Override public boolean test(JavaClass type) { return inputTypes.contains(type.getName()); }
        }).should(new ArchCondition<>("keep input and result facts inside their provider") {
            @Override public void check(JavaClass input, ConditionEvents events) {
                input.getDirectDependenciesFromSelf().forEach(dep -> {
                    String targetOwner = feature(dep.getTargetClass());
                    if (targetOwner != null && !targetOwner.equals(feature(input))) {
                        events.add(SimpleConditionEvent.violated(input, dep.getDescription()));
                    }
                });
            }
        });
    }

    private static final ArchRule THIN_ALL_SPI_ADAPTERS = classes().that(new DescribedPredicate<>("implement a catalogued SPI") {
        @Override public boolean test(JavaClass type) { return !type.isInterface() && SPI_BINDINGS.keySet().stream().anyMatch(type::isAssignableTo); }
    }).should(new ArchCondition<>("use only their SPI, own approved input facts, and technical wiring") {
        @Override public void check(JavaClass adapter, ConditionEvents events) {
            adapter.getDirectDependenciesFromSelf().forEach(dep -> {
                JavaClass type = dep.getTargetClass();
                String name = baseTypeName(type);
                boolean ownInput = java.util.Objects.equals(feature(adapter), feature(type))
                        && (LOAN_APPROVAL_INPUT_TYPES.contains(name) || S5_PROVIDER_INPUT_TYPES.contains(name));
                boolean ownSpi = SPI_BINDINGS.keySet().stream().filter(adapter::isAssignableTo)
                        .anyMatch(spi -> name.equals(spi) || name.startsWith(spi + "$"));
                // Review history deliberately reuses the published case cycle record.
                boolean historyCycle = adapter.isAssignableTo(ROOT + "approval.application.spi.loan.ApprovalLoanReviewHistorySpi")
                        && name.equals(ROOT + "approval.application.spi.loan.ApprovalLoanCaseSpi$ReviewCycleSnapshot");
                if (!technical(type) && !ownInput && !ownSpi && !historyCycle
                        && !name.equals(adapter.getName()) && !name.equals("org.springframework.stereotype.Component")) {
                    events.add(SimpleConditionEvent.violated(adapter, dep.getDescription()));
                }
            });
        }
    });

    private static final ArchRule SPI_PUBLICATION = classes().that().resideInAPackage("..application.spi..")
            .should(new ArchCondition<>("belong to the exact nine-SPI publication catalogue") {
                @Override public void check(JavaClass type, ConditionEvents events) {
                    if ((!APPROVAL_SPI_TYPES.contains(type.getName()) && !DOCUMENT_SPI_TYPES.contains(type.getName()))
                            || (!type.isInterface() && !type.isRecord() && !type.isEnum())) {
                        events.add(SimpleConditionEvent.violated(type, "Unpublished SPI type " + type.getName()));
                    }
                }
            });

    private static boolean persistence(JavaClass type) {
        while (type.isArray()) type = type.getComponentType();
        return type.getPackageName().contains(".persistence")
                || type.getPackageName().contains(".application.port.out")
                || type.isAnnotatedWith("jakarta.persistence.Entity")
                || type.isAssignableTo("org.springframework.data.repository.Repository");
    }

    private static final ArchRule FOREIGN_PERSISTENCE = classes().should(new ArchCondition<>("never enter foreign persistence or output ports") {
        @Override public void check(JavaClass origin, ConditionEvents events) {
            String owner = feature(origin);
            origin.getDirectDependenciesFromSelf().forEach(dep -> {
                String provider = feature(dep.getTargetClass());
                if (owner != null && provider != null && !owner.equals(provider) && persistence(dep.getTargetClass())) {
                    events.add(SimpleConditionEvent.violated(origin, dep.getDescription()));
                }
            });
        }
    });

    private static final ArchRule WEB_AND_EVENT_BOUNDARIES = noClasses()
            .that().resideInAnyPackage("..infrastructure.adapter.in.web..", "..infrastructure.adapter.in.event..")
            .should().dependOnClassesThat(new DescribedPredicate<>("are persistence, output ports, domain services, or use-case implementations") {
                @Override public boolean test(JavaClass type) {
                    return persistence(type) || type.getPackageName().contains(".domain.service")
                            || type.getPackageName().contains(".application.service");
                }
            });

    private static final ArchRule SHARED_INDEPENDENCE = classes().that().resideInAPackage("..shared..")
            .should().onlyDependOnClassesThat(new DescribedPredicate<>("are outside all feature modules") {
                @Override public boolean test(JavaClass type) { return feature(type) == null; }
            });

    private static final ArchRule DOMAIN_PURITY = classes().that().resideInAPackage("..domain..")
            .should(new ArchCondition<>("depend only on JDK, own domain, and shared domain abstractions") {
                @Override public void check(JavaClass origin, ConditionEvents events) {
                    origin.getDirectDependenciesFromSelf().forEach(dep -> {
                        JavaClass target = dep.getTargetClass();
                        while (target.isArray()) target = target.getComponentType();
                        String name = target.getPackageName();
                        if (!jdk(target)
                                && !name.startsWith(ROOT + "shared.domain")
                                && !(java.util.Objects.equals(feature(origin), feature(target)) && name.contains(".domain."))) {
                            events.add(SimpleConditionEvent.violated(origin, dep.getDescription()));
                        }
                    });
                }
            });

    private static final ArchRule PRODUCT_PLACEMENT = classes().should(new ArchCondition<>("use only the eight approved feature roots and shared kernel") {
        @Override public void check(JavaClass type, ConditionEvents events) {
            String name = type.getPackageName();
            if (name.startsWith(ROOT) && !name.equals("com.meridian.platform") && feature(type) == null
                    && !name.startsWith(ROOT + "shared")) {
                events.add(SimpleConditionEvent.violated(type, "Unapproved top-level feature: " + name));
            }
        }
    });

    private static java.util.Map<String, Set<String>> featureGraph(JavaClasses imported) {
        return graphOf(imported.stream().toList());
    }

    private static java.util.List<Set<String>> stronglyConnectedComponents(java.util.Map<String, Set<String>> graph) {
        var reachable = new java.util.TreeMap<String, Set<String>>();
        graph.forEach((name, edges) -> { var paths = new java.util.TreeSet<>(edges); paths.add(name); reachable.put(name, paths); });
        for (String via : graph.keySet()) for (String from : graph.keySet()) {
            if (reachable.get(from).contains(via)) reachable.get(from).addAll(reachable.get(via));
        }
        var remaining = new java.util.TreeSet<>(graph.keySet());
        var components = new java.util.ArrayList<Set<String>>();
        while (!remaining.isEmpty()) {
            String from = remaining.first();
            var component = new java.util.TreeSet<String>();
            for (String to : remaining) if (reachable.get(from).contains(to) && reachable.get(to).contains(from)) component.add(to);
            remaining.removeAll(component);
            components.add(component);
        }
        return components;
    }

    private static final ArchRule FEATURE_ACYCLICITY = classes().should(new ArchCondition<>("form an acyclic eight-feature graph including published/event edges") {
        private final java.util.Set<JavaClass> types = new java.util.HashSet<>();
        @Override public void init(java.util.Collection<JavaClass> allObjects) { types.clear(); }
        @Override public void check(JavaClass type, ConditionEvents events) { types.add(type); }
        @Override public void finish(ConditionEvents events) {
            // Use the same graph builder for production and independently imported fixtures.
            var graph = graphOf(types);
            for (Set<String> component : stronglyConnectedComponents(graph)) if (component.size() > 1) {
                events.add(SimpleConditionEvent.violated(graph, "Feature cycle " + component + "; calculated graph " + graph));
            }
        }
    });

    private static java.util.Map<String, Set<String>> graphOf(java.util.Collection<JavaClass> types) {
        var graph = new java.util.TreeMap<String, Set<String>>();
        FEATURES.forEach(name -> graph.put(name, new java.util.TreeSet<>()));
        types.forEach(type -> {
            String owner = feature(type);
            if (owner == null) return;
            type.getDirectDependenciesFromSelf().forEach(dep -> {
                String provider = feature(dep.getTargetClass());
                if (provider != null && !owner.equals(provider)) graph.get(owner).add(provider);
            });
        });
        return graph;
    }

    @Test
    void allFeatureBoundariesAndPublicSupportClosuresAreExplicit() {
        PUBLIC_BOUNDARIES.check(importedClasses);
        publishedClosure(PUBLIC_TYPES).check(importedClasses);
        FOREIGN_PERSISTENCE.check(importedClasses);
        WEB_AND_EVENT_BOUNDARIES.check(importedClasses);
        DOMAIN_PURITY.check(importedClasses);
        SHARED_INDEPENDENCE.check(importedClasses);
        PRODUCT_PLACEMENT.check(importedClasses);
    }

    @Test
    void allNineSpisHaveOneDesignatedThinProviderAndNoRetiredPrivateBindings() {
        SPI_PUBLICATION.check(importedClasses);
        ALL_SPI_IMPLEMENTERS.check(importedClasses);
        THIN_ALL_SPI_ADAPTERS.check(importedClasses);
        SPI_PROVIDER_ISOLATION.check(importedClasses);
        publishedClosure(S5_PROVIDER_INPUT_TYPES).check(importedClasses);
        providerInputOwnership(S5_PROVIDER_INPUT_TYPES).check(importedClasses);
        SPI_BINDINGS.forEach((spi, adapter) -> {
            var implementations = importedClasses.stream().filter(type -> !type.isInterface() && type.isAssignableTo(spi))
                    .map(JavaClass::getName).toList();
            org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(adapter), implementations, spi);
        });
        for (String retired : Set.of("LoanDocumentWorkflowPort", "LoanDocumentCorrectionPort", "LoanDocumentReviewCorrectionPort",
                "LoanAssistedOriginationPort", "LoanAssistedActionAuthorizationPort", "DocumentStaffActorDirectoryPort")) {
            assertTrue(importedClasses.stream().noneMatch(type -> type.getSimpleName().equals(retired)), retired);
        }
    }

    @Test
    void calculatedProductionGraphHasEightSingletonComponentsAndMatchesAcceptedTarget() {
        FEATURE_ACYCLICITY.check(importedClasses);
        var graph = featureGraph(importedClasses);
        var components = stronglyConnectedComponents(graph);
        System.out.println("Calculated feature graph: " + graph);
        System.out.println("Calculated feature SCCs: " + components);
        org.junit.jupiter.api.Assertions.assertEquals(8, components.size());
        org.junit.jupiter.api.Assertions.assertEquals(java.util.Map.of(
                "loan", Set.of("approval", "customer", "document", "identity", "partner"),
                "approval", Set.of("customer", "identity"), "partner", Set.of("customer"),
                "identity", Set.of("customer", "document", "notification"), "customer", Set.of("document"),
                "document", Set.of(), "audit", Set.of(), "notification", Set.of()), graph);
    }


    @Test
    void cycleRuleRejectsAnOtherwiseLegalPublishedInputAndEventCycle() {
        Class<?> input = com.meridian.platform.testsupport.architecture.identity.application.port.in.CycleInput.class;
        Class<?> event = com.meridian.platform.testsupport.architecture.document.application.event.CycleEvent.class;
        var fixtures = new ClassFileImporter().importClasses(input, event);
        var published = new java.util.HashSet<>(PUBLIC_TYPES);
        published.add(input.getName()); published.add(event.getName());
        publicBoundaries(published).check(fixtures);
        publishedClosure(published).check(fixtures);
        var result = FEATURE_ACYCLICITY.evaluate(fixtures);
        assertTrue(result.hasViolation());
        assertTrue(result.getFailureReport().toString().contains("[document, identity]"));
    }

    @Test
    void publicationRuleRejectsPrivateInputsInAnOtherwiseAcyclicGraph() {
        Class<?> fixture = com.meridian.platform.testsupport.architecture.customer.infrastructure.ForeignInternal.class;
        FEATURE_ACYCLICITY.check(new ClassFileImporter().importClasses(fixture));
        assertRuleRejects(PUBLIC_BOUNDARIES, fixture, "LoanDocumentWorkflowUseCase");
        assertRuleRejects(SPI_PUBLICATION,
                com.meridian.platform.testsupport.architecture.document.application.spi.loan.UnpublishedSpi.class,
                "Unpublished SPI type");
    }

    @Test
    void documentSpiRulesRejectWrongImplementersForeignProviderDependenciesAndRepositories() {
        assertRuleRejects(ALL_SPI_IMPLEMENTERS,
                com.meridian.platform.testsupport.architecture.customer.infrastructure.WrongDocumentSpi.class,
                "LoanDocumentWorkflowSpi");
        assertRuleRejects(SPI_PROVIDER_ISOLATION,
                com.meridian.platform.testsupport.architecture.loan.application.ForeignDocumentSpi.class,
                "LoanDocumentWorkflowSpi");
        assertRuleRejects(THIN_ALL_SPI_ADAPTERS,
                com.meridian.platform.testsupport.architecture.loan.infrastructure.adapter.in.document.RepositoryLeak.class,
                "LoanApplicationRepository");
    }

    @Test
    void publicationClosureRejectsAnApprovedNestedRecordWithDeepGenericInternalTypes() {
        Class<?> fixture = com.meridian.platform.testsupport.architecture.document.application.spi.loan.GenericLeak.class;
        Class<?> nested = fixture.getDeclaredClasses()[0];
        var published = new java.util.HashSet<>(PUBLIC_TYPES);
        published.add(fixture.getName()); published.add(nested.getName());
        var result = publishedClosure(published).evaluate(new ClassFileImporter().importClasses(fixture, nested));
        assertTrue(result.hasViolation());
        assertTrue(result.getFailureReport().toString().contains("LoanApplication"));
    }

    @Test
    void providerInputResultCannotBeOwnedByAnotherFeatureEvenWhenPublished() {
        Class<?> fixture = com.meridian.platform.testsupport.architecture.loan.application.port.in.ForeignResult.class;
        var inputs = new java.util.HashSet<>(S5_PROVIDER_INPUT_TYPES);
        inputs.add(fixture.getName());
        assertRuleRejects(providerInputOwnership(inputs), fixture, "StaffActorSummary");
    }

    @Test
    void sharedPersistenceOrdinaryAndEventRulesRejectIndependentFixtures() {
        assertRuleRejects(SHARED_INDEPENDENCE,
                com.meridian.platform.testsupport.architecture.shared.FeatureLeak.class, "RegisterCustomerUseCase");
        Class<?> persistence = com.meridian.platform.testsupport.architecture.customer.infrastructure.ForeignPersistence.class;
        assertRuleRejects(FOREIGN_PERSISTENCE, persistence, "LoanApplicationJpaEntity");
        assertRuleRejects(FOREIGN_PERSISTENCE, persistence, "LoanApplicationRepository");
        assertRuleRejects(PUBLIC_BOUNDARIES,
                com.meridian.platform.testsupport.architecture.loan.infrastructure.adapter.out.ForeignService.class,
                "QueryCustomerReadinessService");
        assertRuleRejects(WEB_AND_EVENT_BOUNDARIES,
                com.meridian.platform.testsupport.architecture.document.infrastructure.adapter.in.event.EventRepositoryLeak.class,
                "DocumentRepository");
    }

}
