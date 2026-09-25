package in.vedchangani.parallax.backend.strategy;

import java.time.Instant;

/**
 * An immutable, backend-internal view of a {@link StrategyVersion} (D-31),
 * without its definition. Never the entity itself.
 */
public record StrategyVersionSummary(long strategyId, int versionNumber, int schemaVersion, String definitionHash,
                                      Instant createdAt) {

    static StrategyVersionSummary of(StrategyVersion version) {
        return new StrategyVersionSummary(version.strategyId(), version.versionNumber(),
                version.definitionSchemaVersion(), version.definitionHash(), version.createdAt());
    }
}
