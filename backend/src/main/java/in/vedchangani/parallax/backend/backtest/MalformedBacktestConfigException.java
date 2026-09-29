package in.vedchangani.parallax.backend.backtest;

public final class MalformedBacktestConfigException extends RuntimeException {

    private final String path;

    public MalformedBacktestConfigException(String path, String reason) {
        super(path.isEmpty() ? reason : path + ": " + reason);
        this.path = path;
    }

    public String path() {
        return path;
    }
}
