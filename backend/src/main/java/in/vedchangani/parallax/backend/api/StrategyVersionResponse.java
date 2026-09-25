package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.strategy.StrategyVersionDetail;
import in.vedchangani.parallax.backend.strategy.definition.StrategyDefinitionDto;
import in.vedchangani.parallax.backend.strategy.definition.StrategyDefinitionMapper;

import java.time.Instant;

/**
 * The response shape for a single {@code StrategyVersion}, including its
 * definition (D-31). {@code definition} is the D-30 <em>transport</em> DTO
 * shape — produced by {@link StrategyDefinitionMapper#toDto}, never the
 * stored canonical JSON text and never carrying {@code schemaVersion} (that
 * property belongs only to the stored document) — so this exact body can be
 * posted back to {@code POST .../versions} unchanged and produce the same
 * {@code definitionHash}.
 */
public record StrategyVersionResponse(long strategyId, int versionNumber, int schemaVersion, String definitionHash,
                                       Instant createdAt, StrategyDefinitionDto definition) {

    static StrategyVersionResponse of(StrategyVersionDetail detail, StrategyDefinitionMapper mapper) {
        var summary = detail.summary();
        return new StrategyVersionResponse(summary.strategyId(), summary.versionNumber(), summary.schemaVersion(),
                summary.definitionHash(), summary.createdAt(), mapper.toDto(detail.definition()));
    }
}
