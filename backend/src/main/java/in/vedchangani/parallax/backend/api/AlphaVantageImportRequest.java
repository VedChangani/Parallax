package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.marketdata.HistoryDepth;
import jakarta.validation.constraints.NotNull;

/**
 * The {@code POST .../versions/alpha-vantage} request envelope (D-33 Batch
 * 4). Read by the same strict D-30 reader used for every other backend
 * request body — {@code StrategyDefinitionCodec.parseRequest(String,
 * Class)} — never Spring's global JSON binding, so an unknown property, a
 * missing property, an explicit JSON {@code null}, or an unrecognized
 * {@code historyDepth} string all fail before Bean Validation ever runs
 * (mirroring {@link CreateDatasetRequest}). {@code @NotNull} is
 * defense-in-depth, not the primary enforcement.
 *
 * <p>No API key field exists here or anywhere in this request shape — the
 * key is configuration only (D-33 Batch 2's {@code AlphaVantageProperties}),
 * never accepted from a client.
 */
public record AlphaVantageImportRequest(@NotNull HistoryDepth historyDepth) {
}
