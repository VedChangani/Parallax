package in.vedchangani.parallax.backend.backtest;

import in.vedchangani.parallax.engine.data.Bar;
import in.vedchangani.parallax.engine.data.BarSeries;
import in.vedchangani.parallax.engine.result.BacktestConfig;

import java.time.LocalDate;
import java.util.List;

/**
 * Strict raw dataset coverage validation for a {@link BacktestConfig}
 * against a verified {@link BarSeries} (D-34 Batch 2, approved design):
 *
 * <ol>
 *   <li>{@code config.startDate() >= dataset.firstDate()}</li>
 *   <li>{@code config.endDate() <= dataset.lastDate()}</li>
 *   <li>{@code startDate <= endDate} is already guaranteed by {@link
 *   BacktestConfig} itself</li>
 *   <li>at least one bar date must fall inside
 *   {@code [startDate, endDate]}</li>
 * </ol>
 *
 * <p>Deliberately does <strong>not</strong>: calculate indicator warm-up,
 * reject a range merely because the strategy's indicators never become
 * ready inside it, trim the supplied {@link BarSeries}, or inspect bars
 * beyond {@code endDate} to justify a signal. {@code Backtester}'s own
 * {@code firstEvaluableDate} and final-bar semantics remain the sole
 * authority for all of that (CLAUDE.md).
 */
final class BacktestRangeValidator {

    private BacktestRangeValidator() {
    }

    /**
     * @throws BacktestRangeException if the range fails any of the three
     *                                 checks above
     */
    static void validate(BarSeries series, BacktestConfig config) {
        List<Bar> bars = series.bars();
        LocalDate datasetFirstDate = bars.get(0).date();
        LocalDate datasetLastDate = bars.get(bars.size() - 1).date();
        LocalDate startDate = config.startDate();
        LocalDate endDate = config.endDate();

        if (startDate.isBefore(datasetFirstDate)) {
            throw new BacktestRangeException(startDate, endDate, datasetFirstDate, datasetLastDate,
                    "startDate (%s) is before the dataset's first date (%s)".formatted(startDate, datasetFirstDate));
        }
        if (endDate.isAfter(datasetLastDate)) {
            throw new BacktestRangeException(startDate, endDate, datasetFirstDate, datasetLastDate,
                    "endDate (%s) is after the dataset's last date (%s)".formatted(endDate, datasetLastDate));
        }

        boolean hasInRangeBar = bars.stream()
                .anyMatch(bar -> !bar.date().isBefore(startDate) && !bar.date().isAfter(endDate));
        if (!hasInRangeBar) {
            throw new BacktestRangeException(startDate, endDate, datasetFirstDate, datasetLastDate,
                    "no bar exists within the requested range [%s, %s]".formatted(startDate, endDate));
        }
    }
}
