package in.vedchangani.parallax.engine.result;

import in.vedchangani.parallax.engine.execution.Fill;
import in.vedchangani.parallax.engine.indicator.IndicatorSnapshot;
import in.vedchangani.parallax.engine.indicator.IndicatorSpec;
import in.vedchangani.parallax.engine.indicator.IndicatorType;
import in.vedchangani.parallax.engine.strategy.SignalEvent;
import in.vedchangani.parallax.engine.strategy.SignalType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeTest {

    private static final IndicatorSpec SMA_20 = new IndicatorSpec(IndicatorType.SMA, 20);

    private static IndicatorSnapshot snapshot(LocalDate date) {
        return new IndicatorSnapshot(date, new BigDecimal("100"), Map.of(SMA_20, 100.0));
    }

    private static final SignalEvent ENTER = new SignalEvent(SignalType.ENTER, snapshot(LocalDate.of(2024, 1, 1)));
    private static final SignalEvent EXIT = new SignalEvent(SignalType.EXIT, snapshot(LocalDate.of(2024, 1, 5)));

    private static Fill buy(int orderId, LocalDate date, long quantity, BigDecimal price, BigDecimal commission) {
        return new Fill(orderId, date, quantity, price, price, commission, ENTER);
    }

    private static Fill sell(int orderId, LocalDate date, long quantity, BigDecimal price, BigDecimal commission) {
        return new Fill(orderId, date, quantity, price, price, commission, EXIT);
    }

    private static final LocalDate ENTRY_DATE = LocalDate.of(2024, 1, 2);
    private static final LocalDate EXIT_DATE = LocalDate.of(2024, 1, 3);

    // --- Open ----------------------------------------------------------------

    @Test
    void openTradeExposesEntryAndQuantity() {
        Fill entry = buy(1, ENTRY_DATE, 10, new BigDecimal("100"), new BigDecimal("5"));
        Trade.Open trade = new Trade.Open(entry);

        assertEquals(entry, trade.entry());
        assertEquals(10, trade.quantity());
    }

    @Test
    void openNullEntryRejected() {
        assertThrows(NullPointerException.class, () -> new Trade.Open(null));
    }

    @Test
    void openSellEntryRejected() {
        Fill sellFill = sell(1, ENTRY_DATE, 10, new BigDecimal("100"), new BigDecimal("5"));

        assertThrows(IllegalArgumentException.class, () -> new Trade.Open(sellFill));
    }

    @Test
    void openTradesEqualityAndHashCode() {
        Fill entry = buy(1, ENTRY_DATE, 10, new BigDecimal("100"), new BigDecimal("5"));

        assertEquals(new Trade.Open(entry), new Trade.Open(entry));
        assertEquals(new Trade.Open(entry).hashCode(), new Trade.Open(entry).hashCode());
    }

    // --- Closed ----------------------------------------------------------------

    @Test
    void validClosedTradeExposesEntryAndExit() {
        Fill entry = buy(1, ENTRY_DATE, 10, new BigDecimal("100"), new BigDecimal("5"));
        Fill exit = sell(2, EXIT_DATE, 10, new BigDecimal("120"), new BigDecimal("5"));
        Trade.Closed trade = new Trade.Closed(entry, exit);

        assertEquals(entry, trade.entry());
        assertEquals(exit, trade.exit());
        assertEquals(10, trade.quantity());
    }

    @Test
    void winningTradeRealizedPnlMatchesPortfolio() {
        Fill entry = buy(1, ENTRY_DATE, 10, new BigDecimal("100"), new BigDecimal("5"));
        Fill exit = sell(2, EXIT_DATE, 10, new BigDecimal("120"), new BigDecimal("5"));
        Trade.Closed trade = new Trade.Closed(entry, exit);

        assertEquals(0, trade.realizedPnl().compareTo(new BigDecimal("190")));
    }

    @Test
    void losingTradeRealizedPnl() {
        Fill entry = buy(1, ENTRY_DATE, 10, new BigDecimal("100"), new BigDecimal("5"));
        Fill exit = sell(2, EXIT_DATE, 10, new BigDecimal("90"), new BigDecimal("5"));
        Trade.Closed trade = new Trade.Closed(entry, exit);

        assertEquals(0, trade.realizedPnl().compareTo(new BigDecimal("-110")));
    }

    @Test
    void totalCommissionSumsBothFills() {
        Fill entry = buy(1, ENTRY_DATE, 10, new BigDecimal("100"), new BigDecimal("5"));
        Fill exit = sell(2, EXIT_DATE, 10, new BigDecimal("120"), new BigDecimal("5"));
        Trade.Closed trade = new Trade.Closed(entry, exit);

        assertEquals(0, trade.totalCommission().compareTo(new BigDecimal("10")));
    }

    @Test
    void totalSlippageCostSumsBothFills() {
        Fill entry = new Fill(1, ENTRY_DATE, 10, new BigDecimal("100"), new BigDecimal("100.10"),
                BigDecimal.ZERO, ENTER);
        Fill exit = new Fill(2, EXIT_DATE, 10, new BigDecimal("120"), new BigDecimal("119.90"),
                BigDecimal.ZERO, EXIT);
        Trade.Closed trade = new Trade.Closed(entry, exit);

        BigDecimal expected = entry.slippageCost().add(exit.slippageCost());
        assertEquals(0, trade.totalSlippageCost().compareTo(expected));
        assertEquals(0, trade.totalSlippageCost().compareTo(new BigDecimal("2.00")));
    }

    @Test
    void closedNullEntryRejected() {
        Fill exit = sell(2, EXIT_DATE, 10, new BigDecimal("120"), new BigDecimal("5"));

        assertThrows(NullPointerException.class, () -> new Trade.Closed(null, exit));
    }

    @Test
    void closedNullExitRejected() {
        Fill entry = buy(1, ENTRY_DATE, 10, new BigDecimal("100"), new BigDecimal("5"));

        assertThrows(NullPointerException.class, () -> new Trade.Closed(entry, null));
    }

    @Test
    void closedWrongEntrySideRejected() {
        Fill wrongEntry = sell(1, ENTRY_DATE, 10, new BigDecimal("100"), new BigDecimal("5"));
        Fill exit = sell(2, EXIT_DATE, 10, new BigDecimal("120"), new BigDecimal("5"));

        assertThrows(IllegalArgumentException.class, () -> new Trade.Closed(wrongEntry, exit));
    }

    @Test
    void closedWrongExitSideRejected() {
        Fill entry = buy(1, ENTRY_DATE, 10, new BigDecimal("100"), new BigDecimal("5"));
        Fill wrongExit = buy(2, EXIT_DATE, 10, new BigDecimal("120"), new BigDecimal("5"));

        assertThrows(IllegalArgumentException.class, () -> new Trade.Closed(entry, wrongExit));
    }

    @Test
    void mismatchedQuantityRejected() {
        Fill entry = buy(1, ENTRY_DATE, 10, new BigDecimal("100"), new BigDecimal("5"));
        Fill exit = sell(2, EXIT_DATE, 5, new BigDecimal("120"), new BigDecimal("5"));

        assertThrows(IllegalArgumentException.class, () -> new Trade.Closed(entry, exit));
    }

    @Test
    void equalDatesRejected() {
        Fill entry = buy(1, ENTRY_DATE, 10, new BigDecimal("100"), new BigDecimal("5"));
        Fill exit = sell(2, ENTRY_DATE, 10, new BigDecimal("120"), new BigDecimal("5"));

        assertThrows(IllegalArgumentException.class, () -> new Trade.Closed(entry, exit));
    }

    @Test
    void exitBeforeEntryRejected() {
        Fill entry = buy(1, EXIT_DATE, 10, new BigDecimal("100"), new BigDecimal("5"));
        Fill exit = sell(2, ENTRY_DATE, 10, new BigDecimal("120"), new BigDecimal("5"));

        assertThrows(IllegalArgumentException.class, () -> new Trade.Closed(entry, exit));
    }

    @Test
    void exitOrderIdLessThanOrEqualToEntryRejected() {
        Fill entry = buy(2, ENTRY_DATE, 10, new BigDecimal("100"), new BigDecimal("5"));
        Fill exitEqualId = sell(2, EXIT_DATE, 10, new BigDecimal("120"), new BigDecimal("5"));
        Fill exitLowerId = sell(1, EXIT_DATE, 10, new BigDecimal("120"), new BigDecimal("5"));

        assertThrows(IllegalArgumentException.class, () -> new Trade.Closed(entry, exitEqualId));
        assertThrows(IllegalArgumentException.class, () -> new Trade.Closed(entry, exitLowerId));
    }

    // --- fromFills -------------------------------------------------------------

    @Test
    void emptyFillsGiveEmptyTradeList() {
        assertEquals(List.of(), Trade.fromFills(List.of()));
    }

    @Test
    void singleBuyGivesOneOpenTrade() {
        Fill entry = buy(1, ENTRY_DATE, 10, new BigDecimal("100"), new BigDecimal("5"));

        List<Trade> trades = Trade.fromFills(List.of(entry));

        assertEquals(1, trades.size());
        assertTrue(trades.get(0) instanceof Trade.Open);
        assertEquals(entry, ((Trade.Open) trades.get(0)).entry());
    }

    @Test
    void buyThenSellGivesOneClosedTrade() {
        Fill entry = buy(1, ENTRY_DATE, 10, new BigDecimal("100"), new BigDecimal("5"));
        Fill exit = sell(2, EXIT_DATE, 10, new BigDecimal("120"), new BigDecimal("5"));

        List<Trade> trades = Trade.fromFills(List.of(entry, exit));

        assertEquals(1, trades.size());
        assertTrue(trades.get(0) instanceof Trade.Closed);
    }

    @Test
    void buySellBuyGivesClosedThenOpen() {
        Fill entry1 = buy(1, LocalDate.of(2024, 1, 1), 10, new BigDecimal("100"), new BigDecimal("5"));
        Fill exit1 = sell(2, LocalDate.of(2024, 1, 2), 10, new BigDecimal("120"), new BigDecimal("5"));
        Fill entry2 = buy(3, LocalDate.of(2024, 1, 3), 5, new BigDecimal("110"), new BigDecimal("5"));

        List<Trade> trades = Trade.fromFills(List.of(entry1, exit1, entry2));

        assertEquals(2, trades.size());
        assertTrue(trades.get(0) instanceof Trade.Closed);
        assertTrue(trades.get(1) instanceof Trade.Open);
    }

    @Test
    void sellFirstRejected() {
        Fill exit = sell(1, ENTRY_DATE, 10, new BigDecimal("120"), new BigDecimal("5"));

        assertThrows(IllegalArgumentException.class, () -> Trade.fromFills(List.of(exit)));
    }

    @Test
    void buyFollowedByBuyRejected() {
        Fill entry1 = buy(1, LocalDate.of(2024, 1, 1), 10, new BigDecimal("100"), new BigDecimal("5"));
        Fill entry2 = buy(2, LocalDate.of(2024, 1, 2), 10, new BigDecimal("100"), new BigDecimal("5"));

        assertThrows(IllegalArgumentException.class, () -> Trade.fromFills(List.of(entry1, entry2)));
    }

    @Test
    void nullFillsListRejected() {
        assertThrows(NullPointerException.class, () -> Trade.fromFills(null));
    }

    @Test
    void nullElementInFillsRejected() {
        List<Fill> fillsWithNull = new ArrayList<>();
        fillsWithNull.add(buy(1, ENTRY_DATE, 10, new BigDecimal("100"), new BigDecimal("5")));
        fillsWithNull.add(null);

        assertThrows(NullPointerException.class, () -> Trade.fromFills(fillsWithNull));
    }

    @Test
    void resultingListIsImmutable() {
        Fill entry = buy(1, ENTRY_DATE, 10, new BigDecimal("100"), new BigDecimal("5"));
        List<Trade> trades = Trade.fromFills(List.of(entry));

        assertThrows(UnsupportedOperationException.class,
                () -> trades.add(new Trade.Open(entry)));
    }

    @Test
    void fromFillsDoesNotSortOrRepairInvalidSequences() {
        // BUY, BUY should fail rather than being silently reordered/repaired.
        Fill entry1 = buy(1, LocalDate.of(2024, 1, 1), 10, new BigDecimal("100"), new BigDecimal("5"));
        Fill entry2 = buy(2, LocalDate.of(2024, 1, 2), 5, new BigDecimal("100"), new BigDecimal("5"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> Trade.fromFills(List.of(entry1, entry2)));
        assertTrue(ex.getMessage().contains("BUY") || ex.getMessage().contains("SELL"));
    }
}
