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

    private final JavaClasses importedClasses = new ClassFileImporter()
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
}
