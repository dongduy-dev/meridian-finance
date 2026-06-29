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
    void sharedMustNotDependOnIdentity() {
        noClasses()
                .that()
                .resideInAPackage("com.meridian.platform.shared..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.meridian.platform.identity..")
                .check(importedClasses);
    }
}
