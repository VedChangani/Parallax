package in.vedchangani.parallax.backend.backtest;

import java.time.LocalDate;

/**
 * The requested {@code [startDate, endDate]} range does not have strict raw
 * coverage in the dataset's actual bars (D-34 Batch 2): {@code startDate}
 * before the dataset's first bar, {@code endDate} after the dataset's last
 * bar, or no bar at all falling inside the requested range (for example a
 * weekend-only range on a daily dataset). This is deliberately unrelated to
 * indicator warm-up: a range in which the strategy's indicators never
 * become ready is still accepted here — {@code Backtester}'s own {@code
 * firstEvaluableDate} remains the sole authority for that (CLAUDE.md: the
 * backend does not duplicate indicator warm-up semantics).
 *
 * <p>Carries both the requested range and the dataset's actual first/last
 * dates so a caller (a later REST error mapping, in Batch 3) can report
 * exactly why the range was rejected.
 */
public final class BacktestRangeException extends RuntimeException {

    private final LocalDate requestedStartDate;
    private final LocalDate requestedEndDate;
    private final LocalDate datasetFirstDate;
    private final LocalDate datasetLastDate;

    public BacktestRangeException(LocalDate requestedStartDate, LocalDate requestedEndDate,
                                   LocalDate datasetFirstDate, LocalDate datasetLastDate, String message) {
        super(message);
        this.requestedStartDate = requestedStartDate;
        this.requestedEndDate = requestedEndDate;
        this.datasetFirstDate = datasetFirstDate;
        this.datasetLastDate = datasetLastDate;
    }

    public LocalDate requestedStartDate() {
        return requestedStartDate;
    }

    public LocalDate requestedEndDate() {
        return requestedEndDate;
    }

    public LocalDate datasetFirstDate() {
        return datasetFirstDate;
    }

    public LocalDate datasetLastDate() {
        return datasetLastDate;
    }
}
