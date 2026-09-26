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
 * Plain-JDBC access to {@code backtest_equity_point} (D-34 Batch 1,
 * mirroring D-32's {@code DatasetBarRepository}). Deliberately not a JPA
 * entity/repository: a natural composite key with no surrogate id and
 * thousands of rows per run gain nothing from an individually managed
 * entity per row. Insert-only, matching the table's own immutability
 * triggers — no update or delete method exists.
 *
 * <p>Package-private: only code inside {@code backend.backtest} may reach
 * this class. {@code @Component}, not {@code @Repository} — mirroring
 * D-32's own reasoning exactly, so no exception-translation interceptor
 * masks a semantically invalid stored row before a future integrity check
 * ever sees it.
 */
@Component
class BacktestEquityPointRepository {

    private static final int BATCH_SIZE = 1000;

    private static final String INSERT_SQL = """
            insert into backtest_equity_point (run_id, bar_date, cash, quantity, cost_basis, realized_pnl, close)
            values (?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_OWNED_SQL = """
            select e.bar_date, e.cash, e.quantity, e.cost_basis, e.realized_pnl, e.close
            from backtest_equity_point e
            join backtest_run r on r.id = e.run_id
            where e.run_id = ? and r.owner_id = ?
            order by e.bar_date
            """;

    private final JdbcTemplate jdbcTemplate;

    BacktestEquityPointRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void insertAll(long runId, List<BacktestEquityPointRow> rows) {
        Objects.requireNonNull(rows, "rows must not be null");
        if (rows.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, BATCH_SIZE,
                (PreparedStatement ps, BacktestEquityPointRow row) -> {
                    ps.setLong(1, runId);
                    ps.setObject(2, row.barDate());
                    ps.setBigDecimal(3, row.cash());
                    ps.setLong(4, row.quantity());
                    ps.setBigDecimal(5, row.costBasis());
                    ps.setBigDecimal(6, row.realizedPnl());
                    ps.setBigDecimal(7, row.close());
                });
    }

    List<BacktestEquityPointRow> findOwned(long runId, long ownerId) {
        return jdbcTemplate.query(SELECT_OWNED_SQL, BacktestEquityPointRepository::mapRow, runId, ownerId);
    }

    private static BacktestEquityPointRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new BacktestEquityPointRow(
                rs.getObject("bar_date", LocalDate.class),
                rs.getBigDecimal("cash"),
                rs.getLong("quantity"),
                rs.getBigDecimal("cost_basis"),
                rs.getBigDecimal("realized_pnl"),
                rs.getBigDecimal("close"));
    }
}
