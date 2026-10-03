package com.meridian.platform.loan.application.service;

import com.meridian.platform.loan.application.port.out.*;
import com.meridian.platform.loan.application.dto.CustomerIdentityReferenceRevealDto;
import com.meridian.platform.loan.domain.model.LoanApplication;
import com.meridian.platform.shared.application.security.*;
import com.meridian.platform.shared.domain.exception.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RevealStaffCustomerIdentityReferenceServiceTest {
    private final LoanApplicationRepository applications = mock(LoanApplicationRepository.class);
    private final CustomerIdentityReferenceRevealPort customers = mock(CustomerIdentityReferenceRevealPort.class);
    private final CurrentUserProvider users = mock(CurrentUserProvider.class);
    private final RevealStaffCustomerIdentityReferenceService service = new RevealStaffCustomerIdentityReferenceService(applications, customers, users);
    private final UUID applicationId = UUID.randomUUID();
    private static final Set<String> PERMISSIONS = Set.of("loan:read", "customer:read", "customer:identity:reveal");
    private AuthenticatedUser actor(String type, UUID customerId, Set<String> roles, Set<String> permissions) {
        return new AuthenticatedUser(UUID.randomUUID(), "fictional@meridian.test", type, customerId, roles, permissions);
    }

    @Test void resolvesOnlyTheExactApplicationCustomerWithoutAssignmentOrVerificationRestrictions() {
        when(users.currentUser()).thenReturn(actor("STAFF", null, Set.of("LOAN_OFFICER"), PERMISSIONS));
        var application = mock(LoanApplication.class);
        UUID customerId = UUID.randomUUID();
        when(application.id()).thenReturn(applicationId);
        when(application.customerId()).thenReturn(customerId);
        when(applications.findById(applicationId)).thenReturn(Optional.of(application));
        when(customers.reveal(customerId, applicationId)).thenReturn(new CustomerIdentityReferenceRevealPort.Result("FICTIONAL-ID-8901"));
        var result = service.reveal(applicationId);
        assertEquals(applicationId, result.loanApplicationId());
        assertEquals("FICTIONAL-ID-8901", result.identityReference());
        verify(customers).reveal(customerId, applicationId);
        assertFalse(result.toString().contains(result.identityReference()));
        assertFalse(new CustomerIdentityReferenceRevealDto(applicationId, result.identityReference()).toString().contains(result.identityReference()));
        assertFalse(new CustomerIdentityReferenceRevealPort.Result(result.identityReference()).toString().contains(result.identityReference()));
    }

    @Test void everyRequiredActorFactAndExactPermissionIsNecessaryBeforeLoadingAnyApplication() {
        var actors = new ArrayList<AuthenticatedUser>();
        actors.add(actor("CUSTOMER", UUID.randomUUID(), Set.of("LOAN_OFFICER"), PERMISSIONS));
        actors.add(actor("CUSTOMER", null, Set.of("LOAN_OFFICER"), PERMISSIONS));
        actors.add(actor("STAFF", UUID.randomUUID(), Set.of("LOAN_OFFICER"), PERMISSIONS));
        for (String role : List.of("APPROVER", "ACCOUNTING_OFFICER", "BACK_OFFICE_ADMIN", "CUSTOM_ROLE"))
            actors.add(actor("STAFF", null, Set.of(role), PERMISSIONS));
        for (String permission : PERMISSIONS) {
            var missing = new HashSet<>(PERMISSIONS); missing.remove(permission);
            actors.add(actor("STAFF", null, Set.of("LOAN_OFFICER"), missing));
        }
        for (String alone : List.of("customer:identity:verify", "customer:read", "loan:read", "customer:identity:reveal"))
            actors.add(actor("STAFF", null, Set.of("LOAN_OFFICER"), Set.of(alone)));
        for (var denied : actors) {
            when(users.currentUser()).thenReturn(denied);
            assertEquals("CUSTOMER_IDENTITY_REFERENCE_ACCESS_DENIED", assertThrows(AuthorizationException.class,
                    () -> service.reveal(applicationId)).getErrorCode());
        }
        verifyNoInteractions(applications, customers);
    }

    @Test void missingApplicationDoesNotCallCustomer() {
        when(users.currentUser()).thenReturn(actor("STAFF", null, Set.of("LOAN_OFFICER"), PERMISSIONS));
        when(applications.findById(applicationId)).thenReturn(Optional.empty());
        assertEquals("LOAN_APPLICATION_NOT_FOUND", assertThrows(EntityNotFoundException.class, () -> service.reveal(applicationId)).getErrorCode());
        verifyNoInteractions(customers);
    }
}
