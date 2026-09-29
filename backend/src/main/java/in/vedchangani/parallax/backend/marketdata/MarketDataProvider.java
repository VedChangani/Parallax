package in.vedchangani.parallax.backend.marketdata;

public interface MarketDataProvider {

    DailyBars fetchDailyBars(String symbol, HistoryDepth depth);
}
