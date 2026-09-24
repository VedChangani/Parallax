package in.vedchangani.parallax.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-29 proof: the Spring context starts against the PostgreSQL
 * Testcontainer (never a developer's local {@code PARALLAX_DB_*}
 * environment), and Flyway runs against that same {@link DataSource}
 * (C5). No application migrations exist yet — this test only proves the
 * foundation wiring: Flyway's own {@code flyway_schema_history} table
 * exists once Flyway has run.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DatabaseFoundationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PostgreSQLContainer<?> postgresContainer;

    @Test
    void dataSourceUrlMatchesTheTestcontainer() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String actualUrl = connection.getMetaData().getURL();
            assertEquals(postgresContainer.getJdbcUrl(), actualUrl);
        }
    }

    @Test
    void flywayRanAgainstTheTestcontainerDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select to_regclass('public.flyway_schema_history') is not null");
             ResultSet resultSet = statement.executeQuery()) {

            assertTrue(resultSet.next());
            assertTrue(resultSet.getBoolean(1));
        }
    }
}
