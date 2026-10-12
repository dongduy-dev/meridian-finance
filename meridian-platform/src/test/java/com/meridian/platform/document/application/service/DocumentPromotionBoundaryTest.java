package com.meridian.platform.document.application.service;

import com.meridian.platform.document.application.port.out.DocumentStoragePort;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DocumentPromotionBoundaryTest {
    @Test
    void onlySharedApplicationCoordinatorCallsStorageCommitAndAllUploadOwnersRouteThroughIt() {
        var classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.meridian.platform");
        Set<Class<?>> uploadOwners = Set.of(TransactionalDocumentUploadService.class,
                IntakeEvidenceService.class, CustomerIdentityEvidenceService.class, AssistedActionEvidenceService.class);
        for (var type : classes) {
            for (var call : type.getMethodCallsFromSelf()) {
                if (call.getTarget().getName().equals("commit")
                        && call.getTargetOwner().isAssignableTo(DocumentStoragePort.class)) {
                    assertEquals(DocumentPromotionService.class.getName(), type.getName(),
                            "Storage promotion must not bypass mandatory scanning");
                }
            }
        }
        for (Class<?> owner : uploadOwners) {
            assertTrue(classes.get(owner).getMethodCallsFromSelf().stream().anyMatch(call ->
                    call.getTarget().getName().equals("promote")
                            && call.getTargetOwner().isEquivalentTo(DocumentPromotionService.class)), owner.getName());
        }
    }
}
