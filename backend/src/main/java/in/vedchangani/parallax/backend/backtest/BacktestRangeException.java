package in.vedchangani.parallax.backend.backtest;

import java.time.LocalDate;

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
