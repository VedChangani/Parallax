package in.vedchangani.parallax.backend.backtest;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Plain-JDBC access to {@code backtest_fill} (D-34 Batch 1, mirroring
 * D-32's {@code DatasetBarRepository}/this package's own {@code
 * BacktestEquityPointRepository}). Insert-only, matching the table's own
 * immutability triggers. {@code signal_indicators} is bound as text cast
 * to {@code jsonb} in the SQL itself ({@code ?::jsonb}) — the same
 * approach D-31's {@code StrategyVersion} uses via Hibernate's {@code
 * @ColumnTransformer}, applied directly here since this is plain JDBC.
 */
@Component
class BacktestFillRepository {

    private static final int BATCH_SIZE = 1000;

    private static final String INSERT_SQL = """
            insert into backtest_fill
                (run_id, order_id, fill_date, quantity, reference_open, fill_price, commission,
                 signal_type, signal_date, signal_close, signal_indicators)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            """;

    private static final String SELECT_OWNED_SQL = """
            select f.order_id, f.fill_date, f.quantity, f.reference_open, f.fill_price, f.commission,
                   f.signal_type, f.signal_date, f.signal_close, f.signal_indicators
            from backtest_fill f
            join backtest_run r on r.id = f.run_id
            where f.run_id = ? and r.owner_id = ?
            order by f.order_id
            """;

    private final JdbcTemplate jdbcTemplate;

    BacktestFillRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void insertAll(long runId, List<BacktestFillRow> rows) {
        Objects.requireNonNull(rows, "rows must not be null");
        if (rows.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, BATCH_SIZE,
                (PreparedStatement ps, BacktestFillRow row) -> {
                    ps.setLong(1, runId);
                    ps.setInt(2, row.orderId());
                    ps.setObject(3, row.fillDate());
                    ps.setLong(4, row.quantity());
                    ps.setBigDecimal(5, row.referenceOpen());
                    ps.setBigDecimal(6, row.fillPrice());
                    ps.setBigDecimal(7, row.commission());
                    ps.setString(8, row.signalType());
                    ps.setObject(9, row.signalDate());
                    ps.setBigDecimal(10, row.signalClose());
                    ps.setString(11, row.signalIndicatorsJson());
                });
    }

    List<BacktestFillRow> findOwned(long runId, long ownerId) {
        return jdbcTemplate.query(SELECT_OWNED_SQL, BacktestFillRepository::mapRow, runId, ownerId);
    }

    private static BacktestFillRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new BacktestFillRow(
                rs.getInt("order_id"),
                rs.getObject("fill_date", LocalDate.class),
                rs.getLong("quantity"),
                rs.getBigDecimal("reference_open"),
                rs.getBigDecimal("fill_price"),
                rs.getBigDecimal("commission"),
                rs.getString("signal_type"),
                rs.getObject("signal_date", LocalDate.class),
                rs.getBigDecimal("signal_close"),
                rs.getString("signal_indicators"));
    }
}
