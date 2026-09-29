package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.marketdata.HistoryDepth;
import jakarta.validation.constraints.NotNull;

public record AlphaVantageImportRequest(@NotNull HistoryDepth historyDepth) {
}
