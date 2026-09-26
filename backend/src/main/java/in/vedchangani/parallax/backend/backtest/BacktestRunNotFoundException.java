package in.vedchangani.parallax.backend.backtest;

/**
 * No {@link BacktestRun} with the requested id is visible to the requesting
 * owner (D-34 Batch 2, mirroring D-31/D-32 §10) — either it does not exist
 * at all, or it belongs to a different owner. Both cases are represented
 * identically, so a client can never distinguish "does not exist" from
 * "isn't yours."
 */
public final class BacktestRunNotFoundException extends RuntimeException {

    public BacktestRunNotFoundException(long runId) {
        super("backtest run not found: " + runId);
    }
}
