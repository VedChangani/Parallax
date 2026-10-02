package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.strategy.StrategyVersionDetail;
import in.vedchangani.parallax.backend.strategy.definition.StrategyDefinitionDto;
import in.vedchangani.parallax.backend.strategy.definition.StrategyDefinitionMapper;

import java.time.Instant;

public record StrategyVersionResponse(long strategyId, int versionNumber, int schemaVersion, String definitionHash,
                                       Instant createdAt, StrategyDefinitionDto definition) {

    static StrategyVersionResponse of(StrategyVersionDetail detail, StrategyDefinitionMapper mapper) {
        var summary = detail.summary();
        return new StrategyVersionResponse(summary.strategyId(), summary.versionNumber(), summary.schemaVersion(),
                summary.definitionHash(), summary.createdAt(), mapper.toDto(detail.definition()));
    }
}
