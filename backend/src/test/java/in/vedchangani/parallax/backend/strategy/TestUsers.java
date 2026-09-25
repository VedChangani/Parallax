package in.vedchangani.parallax.backend.strategy;

import in.vedchangani.parallax.backend.user.UserId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Test-only helper that inserts uniquely named {@code app_user} rows
 * directly via {@link JdbcTemplate} (D-31 test infrastructure). Every test
 * uses fresh users and strategies, so lists are exact with no cleanup step
 * needed — cleanup would be impossible anyway, since the {@code
 * strategy_version} immutability triggers also block truncating the schema
 * between tests.
 */
public final class TestUsers {

    private static final AtomicLong COUNTER = new AtomicLong();

    private TestUsers() {
    }

    public static UserId create(JdbcTemplate jdbcTemplate, String label) {
        String username = label + "-" + System.nanoTime() + "-" + COUNTER.incrementAndGet();
        Long id = jdbcTemplate.queryForObject(
                "insert into app_user (username) values (?) returning id", Long.class, username);
        return new UserId(id);
    }
}
