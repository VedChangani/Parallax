package in.vedchangani.parallax.backend.dataset;

import in.vedchangani.parallax.engine.data.Bar;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Plain-JDBC access to {@code dataset_bar} (D-32). Deliberately not a JPA
 * entity/repository: an assigned composite key (no surrogate id) would make
 * Spring Data's {@code save} issue a {@code merge} (one SELECT per row),
 * and thousands of managed entities per read add nothing for rows that are
 * never individually edited or loaded. {@link JdbcTemplate} batch insert
 * and a single ordered {@code SELECT} are simpler and faster.
 *
 * <p>Package-private: only {@link DatasetService} may reach this class, so
 * a {@link in.vedchangani.parallax.engine.data.BarSeries} can only ever
 * leave the {@code dataset} package through {@code
 * DatasetService#getVerifiedSeries}, never through a raw bar read.
 *
 * <p>Insert-only: there is no update or delete method, matching {@code
 * dataset_bar}'s database-level immutability triggers.
 *
 * <p>Deliberately {@link Component}, not {@code @Repository}: the
 * {@code @Repository} stereotype installs Spring's
 * {@code PersistenceExceptionTranslationInterceptor}, which translates
 * <em>every</em> exception this bean's methods throw — including the
 * {@link Bar} constructor's own {@link IllegalArgumentException} on a
 * semantically invalid stored row — into a
 * {@code DataAccessException} subtype before {@code
 * DatasetService#getVerifiedSeries} ever sees it, masking the exact
 * integrity failure that method exists to catch. {@link JdbcTemplate}
 * already translates genuine {@code SQLException}s into
 * {@code DataAccessException} on its own, independent of this
 * stereotype, so no exception-translation behavior is lost by using
 * {@code @Component} instead.
 */
@Component
class DatasetBarRepository {

    private static final int BATCH_SIZE = 1000;

    private static final String INSERT_SQL = """
            insert into dataset_bar (dataset_version_id, bar_date, open, high, low, close, volume)
            values (?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_OWNED_SQL = """
            select b.bar_date, b.open, b.high, b.low, b.close, b.volume
            from dataset_bar b
            join dataset_version v on v.id = b.dataset_version_id
            join dataset d on d.id = v.dataset_id
            where v.id = ? and d.owner_id = ?
            order by b.bar_date
            """;

    private final JdbcTemplate jdbcTemplate;

    DatasetBarRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Batch-inserts every bar for {@code versionId}, in {@code bars}'
     * given order, using the caller's current transaction/connection (no
     * network I/O of its own — the caller has already parsed and
     * validated {@code bars} before this is invoked).
     */
    void insertAll(long versionId, List<Bar> bars) {
        Objects.requireNonNull(bars, "bars must not be null");
        if (bars.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(INSERT_SQL, bars, BATCH_SIZE,
                (PreparedStatement ps, Bar bar) -> {
                    ps.setLong(1, versionId);
                    ps.setObject(2, bar.date());
                    ps.setBigDecimal(3, bar.open());
                    ps.setBigDecimal(4, bar.high());
                    ps.setBigDecimal(5, bar.low());
                    ps.setBigDecimal(6, bar.close());
                    ps.setLong(7, bar.volume());
                });
    }

    /**
     * Owner-scoped, date-ordered read of every bar for {@code versionId}.
     * Ownership is re-checked here (a join through {@code dataset_version}
     * to {@code dataset.owner_id}) even though the caller has typically
     * already resolved an owned {@link DatasetVersion} — defense in depth,
     * since this class has no other access control of its own.
     */
    List<Bar> findOwned(long versionId, long ownerId) {
        return jdbcTemplate.query(SELECT_OWNED_SQL, DatasetBarRepository::mapBar, versionId, ownerId);
    }

    private static Bar mapBar(ResultSet rs, int rowNum) throws SQLException {
        LocalDate date = rs.getObject("bar_date", LocalDate.class);
        BigDecimal open = rs.getBigDecimal("open");
        BigDecimal high = rs.getBigDecimal("high");
        BigDecimal low = rs.getBigDecimal("low");
        BigDecimal close = rs.getBigDecimal("close");
        long volume = rs.getLong("volume");
        return new Bar(date, open, high, low, close, volume);
    }
}
