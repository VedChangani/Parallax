package in.vedchangani.parallax.backend.backtest;

public final class BacktestResultIntegrityException extends RuntimeException {

    public BacktestResultIntegrityException(String message) {
        super(message);
    }

    public BacktestResultIntegrityException(String message, Throwable cause) {
        super(message, cause);
    }
}
