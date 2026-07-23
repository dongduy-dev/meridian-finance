package com.meridian.platform.testsupport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

public final class DisbursementSnapshotTestEnvironmentPostProcessor implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        environment.getPropertySources().addFirst(new MapPropertySource(
                "contract-readiness-test-key",
                Map.of(
                        "meridian.loan.disbursement-snapshot.active-key-id", "v1",
                        "meridian.loan.disbursement-snapshot.keys.v1", Base64.getEncoder().encodeToString(key)
                )
        ));
        java.util.Arrays.fill(key, (byte) 0);
    }
}
