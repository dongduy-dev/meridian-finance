package com.meridian.platform.identity.infrastructure.adapter.in.bootstrap;

import com.meridian.platform.identity.application.dto.CreateInternalUserRequest;
import com.meridian.platform.identity.application.port.in.BootstrapAdministratorUseCase;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "meridian.identity.bootstrap.enabled", havingValue = "true")
public class AdministratorBootstrapRunner implements ApplicationRunner {
    private final BootstrapAdministratorUseCase bootstrap;
    private final Validator validator;
    private final String email;
    private final String displayName;

    public AdministratorBootstrapRunner(BootstrapAdministratorUseCase bootstrap, Validator validator,
            @Value("${meridian.identity.bootstrap.email:}") String email,
            @Value("${meridian.identity.bootstrap.display-name:}") String displayName) {
        this.bootstrap = bootstrap;
        this.validator = validator;
        this.email = email;
        this.displayName = displayName;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        CreateInternalUserRequest request = new CreateInternalUserRequest(email, displayName, List.of("BACK_OFFICE_ADMIN"));
        if (!validator.validate(request).isEmpty()) {
            throw new IllegalStateException("Administrator bootstrap requires a valid email and display name.");
        }
        bootstrap.bootstrap(request.email(), request.displayName());
    }
}
