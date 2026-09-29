package in.vedchangani.parallax.backend.backtest;

public record BacktestConfigRequest(String initialCapital, String commissionPerFill, String slippageRate,
                                     String startDate, String endDate) {
}
