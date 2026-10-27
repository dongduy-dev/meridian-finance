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
