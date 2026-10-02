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
