package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.strategy.StrategyVersionSummary;

import java.time.Instant;

/**
 * The response shape for a {@code StrategyVersion} listed without its
 * definition (D-31). Never the JPA entity itself.
 */
public record StrategyVersionSummaryResponse(long strategyId, int versionNumber, int schemaVersion,
                                              String definitionHash, Instant createdAt) {

    static StrategyVersionSummaryResponse of(StrategyVersionSummary summary) {
        return new StrategyVersionSummaryResponse(summary.strategyId(), summary.versionNumber(),
                summary.schemaVersion(), summary.definitionHash(), summary.createdAt());
    }
}
