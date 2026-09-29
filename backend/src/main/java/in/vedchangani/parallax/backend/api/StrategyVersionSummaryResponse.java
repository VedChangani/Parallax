package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.strategy.StrategyVersionSummary;

import java.time.Instant;

public record StrategyVersionSummaryResponse(long strategyId, int versionNumber, int schemaVersion,
                                              String definitionHash, Instant createdAt) {

    static StrategyVersionSummaryResponse of(StrategyVersionSummary summary) {
        return new StrategyVersionSummaryResponse(summary.strategyId(), summary.versionNumber(),
                summary.schemaVersion(), summary.definitionHash(), summary.createdAt());
    }
}
