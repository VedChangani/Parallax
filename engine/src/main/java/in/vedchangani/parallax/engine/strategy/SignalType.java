package in.vedchangani.parallax.engine.strategy;

/**
 * What a {@link SignalEvent} intends. There is no third state: V1 is
 * long-only (one position at a time), so the strategy only ever intends
 * to go from flat to long ({@code ENTER}) or from long to flat
 * ({@code EXIT}).
 */
public enum SignalType {
    ENTER,
    EXIT
}
