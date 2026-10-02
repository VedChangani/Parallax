package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.strategy.StrategySummary;

import java.time.Instant;

public record StrategyResponse(long id, String name, String description, int latestVersionNumber,
                                Instant createdAt) {

    static StrategyResponse of(StrategySummary summary) {
        return new StrategyResponse(summary.id(), summary.name(), summary.description(),
                summary.latestVersionNumber(), summary.createdAt());
    }
}
