package in.vedchangani.parallax.backend.strategy;

import java.time.Instant;

public record StrategySummary(long id, String name, String description, int latestVersionNumber,
                               Instant createdAt) {

    static StrategySummary of(Strategy strategy) {
        return new StrategySummary(strategy.id(), strategy.name(), strategy.description(),
                strategy.latestVersionNumber(), strategy.createdAt());
    }
}
