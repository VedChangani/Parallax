package in.vedchangani.parallax.backend.strategy;

import java.time.Instant;

/**
 * An immutable, backend-internal view of a {@link Strategy} (D-31). Never
 * the entity itself — entities do not leave the {@code strategy} package.
 */
public record StrategySummary(long id, String name, String description, int latestVersionNumber,
                               Instant createdAt) {

    static StrategySummary of(Strategy strategy) {
        return new StrategySummary(strategy.id(), strategy.name(), strategy.description(),
                strategy.latestVersionNumber(), strategy.createdAt());
    }
}
