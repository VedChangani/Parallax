package in.vedchangani.parallax.backend.backtest;

import in.vedchangani.parallax.engine.data.Bar;
import in.vedchangani.parallax.engine.data.BarSeries;
import in.vedchangani.parallax.engine.result.BacktestConfig;

import java.time.LocalDate;
import java.util.List;

final class BacktestRangeValidator {

    private BacktestRangeValidator() {
    }

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
