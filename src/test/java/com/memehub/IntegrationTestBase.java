package com.memehub;

import java.util.UUID;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Boots the application against a real pgvector Postgres and a real S3-compatible
 * object store. The containers are shared by every subclass (started once per test
 * run) so the cached Spring context never points at a stopped container.
 * Skipped automatically when Docker is not running.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
public abstract class IntegrationTestBase {

    private static final String S3_ACCESS_KEY = "memehub-test";
    private static final String S3_SECRET_KEY = "memehub-test-secret";
    private static final int S3_PORT = 9000;
    // Fixed for the whole run: every application started by a test shares one database, and the
    // administrator is only created by whichever starts first.
    private static final String JWT_SECRET = "test-" + UUID.randomUUID() + UUID.randomUUID();
    private static final String ADMIN_PASSWORD = "test-" + UUID.randomUUID();

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static final GenericContainer<?> OBJECT_STORAGE = new GenericContainer<>("rustfs/rustfs:latest")
            .withExposedPorts(S3_PORT)
            .withEnv("RUSTFS_ACCESS_KEY", S3_ACCESS_KEY)
            .withEnv("RUSTFS_SECRET_KEY", S3_SECRET_KEY)
            .waitingFor(Wait.forHttp("/health").forPort(S3_PORT).forStatusCode(200));

    static {
        POSTGRES.start();
        OBJECT_STORAGE.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("memehub.storage.endpoint",
                () -> "http://" + OBJECT_STORAGE.getHost() + ":" + OBJECT_STORAGE.getMappedPort(S3_PORT));
        registry.add("memehub.storage.access-key", () -> S3_ACCESS_KEY);
        registry.add("memehub.storage.secret-key", () -> S3_SECRET_KEY);
        // Throwaway secrets for this test run; the real ones live in the git-ignored .env.
        registry.add("memehub.security.jwt.secret", () -> JWT_SECRET);
        registry.add("memehub.security.bootstrap-admin.password", () -> ADMIN_PASSWORD);
        // Fast, failure-free mock model so jobs finish quickly and deterministically.
        registry.add("memehub.llm.mock.min-latency-ms", () -> "0");
        registry.add("memehub.llm.mock.max-latency-ms", () -> "20");
        registry.add("memehub.generation.poll-interval", () -> "PT0.1S");
        registry.add("memehub.tagging.poll-interval", () -> "PT0.1S");
        // Tests trigger the index sync themselves so results are deterministic.
        registry.add("memehub.index.scheduler-enabled", () -> "false");
    }
}
