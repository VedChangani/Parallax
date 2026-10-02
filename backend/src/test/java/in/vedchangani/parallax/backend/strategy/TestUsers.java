package in.vedchangani.parallax.backend.strategy;

import in.vedchangani.parallax.backend.user.UserId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.atomic.AtomicLong;

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
