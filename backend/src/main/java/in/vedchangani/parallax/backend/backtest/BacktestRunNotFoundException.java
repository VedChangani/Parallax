package in.vedchangani.parallax.backend.backtest;

public final class BacktestRunNotFoundException extends RuntimeException {

    public BacktestRunNotFoundException(long runId) {
        super("backtest run not found: " + runId);
    }
}
