package com.meridian.platform.identity.infrastructure.adapter.in.bootstrap;

import com.meridian.platform.identity.application.port.in.BootstrapAdministratorUseCase;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdministratorBootstrapRunnerTest {
    @Test
    void disabledUnlessOperatorExplicitlyEnablesIt() {
        var context = new ApplicationContextRunner().withUserConfiguration(AdministratorBootstrapRunner.class)
                .withBean(BootstrapAdministratorUseCase.class, () -> mock(BootstrapAdministratorUseCase.class))
                .withBean(jakarta.validation.Validator.class, () -> Validation.buildDefaultValidatorFactory().getValidator());
        context.run(c -> assertFalse(c.containsBean("administratorBootstrapRunner")));
        context.withPropertyValues("meridian.identity.bootstrap.enabled=false")
                .run(c -> assertFalse(c.containsBean("administratorBootstrapRunner")));
        context.withPropertyValues("meridian.identity.bootstrap.enabled=true",
                        "meridian.identity.bootstrap.email=operator@meridian.test", "meridian.identity.bootstrap.display-name=Operator")
                .run(c -> {
                    assertNull(c.getStartupFailure());
                    c.getBean(AdministratorBootstrapRunner.class).run(new DefaultApplicationArguments());
                    verify(c.getBean(BootstrapAdministratorUseCase.class)).bootstrap("operator@meridian.test", "Operator");
                });
    }

    @Test
    void invalidOperatorConfigurationFailsWithoutEchoingValuesOrCallingIdentity() {
        var bootstrap = mock(BootstrapAdministratorUseCase.class);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var runner = new AdministratorBootstrapRunner(bootstrap, factory.getValidator(), "invalid-operator-value", "");
            var error = assertThrows(IllegalStateException.class, () -> runner.run(new DefaultApplicationArguments()));
            assertFalse(error.getMessage().contains("invalid-operator-value"));
            verifyNoInteractions(bootstrap);
        }
    }
}
