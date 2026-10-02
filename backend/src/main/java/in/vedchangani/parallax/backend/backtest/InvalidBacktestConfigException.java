package in.vedchangani.parallax.backend.backtest;

import in.vedchangani.parallax.engine.result.BacktestConfig;

public final class InvalidBacktestConfigException extends RuntimeException {

    private final String path;

    public InvalidBacktestConfigException(String path, String engineMessage) {
        super(path.isEmpty() ? engineMessage : path + ": " + engineMessage);
        this.path = path;
    }

    public String path() {
        return path;
    }
}
