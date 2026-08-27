package com.jperez.apitests.config;

public class TestConfig {

    /**
     * Resolves the base URL for the API under test.
     *
     * If the system property/env var API_BASE_URL is set, tests run
     * against that already-running instance (local, Docker Compose,
     * or a deployed Kubernetes cluster).
     *
     * If it is not set, tests fall back to self-contained mode and
     * expect SelfContainedEnvironment to have started its own instance
     * and set this value dynamically before tests run.
     */
    public static String resolveBaseUrl() {
        String fromProperty = System.getProperty("API_BASE_URL");
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty;
        }

        String fromEnv = System.getenv("API_BASE_URL");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }

        throw new IllegalStateException(
                "API_BASE_URL is not set. Either export it to point at a running " +
                        "instance, or run tests via the self-contained profile which sets it automatically."
        );
    }
}
