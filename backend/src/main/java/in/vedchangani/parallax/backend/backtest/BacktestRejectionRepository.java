package in.vedchangani.parallax.backend.backtest;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Component
class BacktestRejectionRepository {

    private static final int BATCH_SIZE = 1000;

    private static final String INSERT_SQL = """
            insert into backtest_rejection
                (run_id, seq, reason, order_id, execution_date, quantity, required_cash, available_cash,
                 signal_date, signal_close, signal_indicators)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            """;

    private static final String SELECT_OWNED_SQL = """
            select rj.seq, rj.reason, rj.order_id, rj.execution_date, rj.quantity, rj.required_cash,
                   rj.available_cash, rj.signal_date, rj.signal_close, rj.signal_indicators
            from backtest_rejection rj
            join backtest_run r on r.id = rj.run_id
            where rj.run_id = ? and r.owner_id = ?
            order by rj.seq
            """;

    private final JdbcTemplate jdbcTemplate;

    BacktestRejectionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void insertAll(long runId, List<BacktestRejectionRow> rows) {
        Objects.requireNonNull(rows, "rows must not be null");
        if (rows.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, BATCH_SIZE,
                (PreparedStatement ps, BacktestRejectionRow row) -> {
                    ps.setLong(1, runId);
                    ps.setInt(2, row.seq());
                    ps.setString(3, row.reason());
                    setNullableInt(ps, 4, row.orderId());
                    setNullableDate(ps, 5, row.executionDate());
                    setNullableLong(ps, 6, row.quantity());
                    setNullableBigDecimal(ps, 7, row.requiredCash());
                    setNullableBigDecimal(ps, 8, row.availableCash());
                    ps.setObject(9, row.signalDate());
                    ps.setBigDecimal(10, row.signalClose());
                    ps.setString(11, row.signalIndicatorsJson());
                });
    }

    List<BacktestRejectionRow> findOwned(long runId, long ownerId) {
        return jdbcTemplate.query(SELECT_OWNED_SQL, BacktestRejectionRepository::mapRow, runId, ownerId);
    }

    private static BacktestRejectionRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new BacktestRejectionRow(
                rs.getInt("seq"),
                rs.getString("reason"),
                rs.getObject("order_id", Integer.class),
                rs.getObject("execution_date", LocalDate.class),
                rs.getObject("quantity", Long.class),
                rs.getBigDecimal("required_cash"),
                rs.getBigDecimal("available_cash"),
                rs.getObject("signal_date", LocalDate.class),
                rs.getBigDecimal("signal_close"),
                rs.getString("signal_indicators"));
    }

    private static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private static void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }

    private static void setNullableDate(PreparedStatement ps, int index, LocalDate value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.DATE);
        } else {
            ps.setObject(index, value);
        }
    }

    private static void setNullableBigDecimal(PreparedStatement ps, int index, BigDecimal value)
            throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.NUMERIC);
        } else {
            ps.setBigDecimal(index, value);
        }
    }
}
