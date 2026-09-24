package in.vedchangani.parallax.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The single test datasource for all backend integration tests: a real
 * PostgreSQL container, wired in via Spring Boot's {@code @ServiceConnection}
 * mechanism (C5, Phase 7 design). This registers a {@code
 * JdbcConnectionDetails} bean that Boot's datasource auto-configuration
 * prefers over {@code spring.datasource.*}, so a developer's local
 * {@code PARALLAX_DB_*} environment variables are never used by tests, and
 * no second/parallel configuration system is introduced.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
    }
}
