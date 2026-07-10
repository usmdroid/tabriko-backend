package uz.tabriko.support;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

// Base class for integration tests needing a real Postgres instance (Flyway migrations,
// native SQL functions, etc. don't behave the same on H2). Deliberately does NOT use the
// @Testcontainers JUnit5 extension: that extension starts @Container fields in its own
// beforeAll callback before any assumption can gate it, so on hosts without a Docker
// daemon the whole build would fail instead of skipping. Starting the container manually
// inside a plain @BeforeAll lets us decide the failure mode explicitly: by default a
// missing Docker daemon fails the build loudly (a `mvn test` with these classes silently
// skipped would misreport a real Docker-dependent regression as "0 failures"); pass
// -DskipDockerTests=true to opt into the old soft-skip behavior on dev machines that
// intentionally don't run Docker.
public abstract class PostgresTestSupport {

    protected static PostgreSQLContainer<?> postgres;

    @BeforeAll
    static void startContainerIfDockerAvailable() {
        boolean dockerAvailable;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            dockerAvailable = false;
        }
        if (!dockerAvailable) {
            if (Boolean.getBoolean("skipDockerTests")) {
                Assumptions.abort("Docker is not available; skipping Testcontainers-backed test (skipDockerTests=true)");
            }
            Assertions.fail("Docker is not available but is required for Testcontainers-backed tests. "
                    + "Start Docker, or explicitly opt out with -DskipDockerTests=true "
                    + "(these tests will then be skipped instead of verified).");
        }

        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("tabriko_test")
                .withUsername("tabriko")
                .withPassword("tabriko");
        postgres.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl());
        registry.add("spring.datasource.username", () -> postgres.getUsername());
        registry.add("spring.datasource.password", () -> postgres.getPassword());
        // Required (no-default) secrets — only needed by @SpringBootTest subclasses that boot
        // the full context, but harmless to set unconditionally for @DataJpaTest subclasses too.
        registry.add("app.jwt.access-secret", () -> "test-access-secret-key-long-enough-for-hmac-sha256-0123456789");
        registry.add("app.jwt.refresh-secret", () -> "test-refresh-secret-key-long-enough-for-hmac-sha256-0123456789");
        registry.add("app.payment.callback-secret", () -> "test-callback-secret");
    }
}
