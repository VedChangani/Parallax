package in.vedchangani.parallax.backend.backtest;

import in.vedchangani.parallax.engine.result.BacktestConfig;

/**
 * A client-supplied {@code BacktestConfig} request is shaped correctly but
 * violates an engine semantic rule (D-34 Batch 2, mirroring D-30's {@code
 * InvalidStrategyDefinitionException}): {@code initialCapital <= 0},
 * {@code commissionPerFill < 0}, {@code slippageRate} outside {@code [0,1)},
 * or {@code startDate} after {@code endDate}. The engine's own {@link
 * BacktestConfig} constructor remains the sole source of truth for these
 * rules; this exception only carries its {@code IllegalArgumentException}
 * message forward with a field path attached, never the engine exception
 * type itself. {@code path} names the offending field (for example
 * {@code "config.slippageRate"}).
 */
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
