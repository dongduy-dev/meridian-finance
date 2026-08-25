package com.meridian.platform;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureRulesTest {

    private final JavaClasses importedClasses = new ClassFileImporter()
            .importPackages("com.meridian.platform");

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
