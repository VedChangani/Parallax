package in.vedchangani.parallax.engine;

import in.vedchangani.parallax.engine.data.Bar;
import in.vedchangani.parallax.engine.data.BarSeries;
import in.vedchangani.parallax.engine.execution.Fill;
import in.vedchangani.parallax.engine.execution.OrderRejection;
import in.vedchangani.parallax.engine.indicator.IndicatorSpec;
import in.vedchangani.parallax.engine.indicator.IndicatorType;
import in.vedchangani.parallax.engine.metrics.BuyAndHoldBenchmark;
import in.vedchangani.parallax.engine.metrics.PerformanceMetrics;
import in.vedchangani.parallax.engine.portfolio.EquityPoint;
import in.vedchangani.parallax.engine.result.BacktestConfig;
import in.vedchangani.parallax.engine.result.BacktestResult;
import in.vedchangani.parallax.engine.result.Trade;
import in.vedchangani.parallax.engine.strategy.Condition;
import in.vedchangani.parallax.engine.strategy.Operand;
import in.vedchangani.parallax.engine.strategy.Operator;
import in.vedchangani.parallax.engine.strategy.PositionSizing;
import in.vedchangani.parallax.engine.strategy.SignalEvent;
import in.vedchangani.parallax.engine.strategy.StrategyDefinition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BacktesterFingerprintTest {

    private static final int BAR_COUNT = 60;
    private static final LocalDate FIRST_DATE = LocalDate.of(2020, 1, 1);

    private static LocalDate dateAt(int i) {
        return FIRST_DATE.plusDays(7L * i);
    }

    private static BigDecimal priceAt(int i) {
        int trendCents = i * 40;
        int pos = i % 12;
        int waveCents = (pos <= 6 ? pos : 12 - pos) * 100;
        int cents = 10_000 + trendCents + waveCents;
        return new BigDecimal(cents).movePointLeft(2);
    }

    private static BarSeries buildSeries() {
        List<Bar> bars = new ArrayList<>(BAR_COUNT);
        for (int i = 0; i < BAR_COUNT; i++) {
            BigDecimal close = priceAt(i);
            bars.add(new Bar(dateAt(i), close, close, close, close, 1_000));
        }
        return new BarSeries("FINGERPRINT", bars);
    }

    private static StrategyDefinition buildStrategy() {
        IndicatorSpec sma10 = new IndicatorSpec(IndicatorType.SMA, 10);
        Condition entry = new Condition.Compare(new Operand.Close(), Operator.GT, new Operand.IndicatorRef(sma10));
        Condition exit = new Condition.Compare(new Operand.Close(), Operator.LT, new Operand.IndicatorRef(sma10));
        return new StrategyDefinition(entry, exit, new PositionSizing.CashFraction(BigDecimal.ONE));
    }

    private static BacktestConfig buildConfig(BarSeries series) {
        List<Bar> bars = series.bars();
        return new BacktestConfig(new BigDecimal("10000"), new BigDecimal("1"), new BigDecimal("0.001"),
                bars.get(0).date(), bars.get(bars.size() - 1).date());
    }

    @Test
    void semanticFingerprint() {
        BarSeries series = buildSeries();
        StrategyDefinition strategy = buildStrategy();
        BacktestConfig config = buildConfig(series);

        BacktestResult result = new Backtester().run(series, strategy, config);
        PerformanceMetrics metrics = PerformanceMetrics.of(result);
        BuyAndHoldBenchmark benchmark = BuyAndHoldBenchmark.of(series, result);

        String fingerprint = fingerprintOf(result, metrics, benchmark);

        assertEquals(EXPECTED_FINGERPRINT, fingerprint,
                "engine semantic fingerprint changed - if this is a deliberate result-affecting "
                        + "change, bump Backtester.SEMANTICS_VERSION; otherwise investigate the drift "
                        + "before updating this expected value");
    }

    private static String fingerprintOf(BacktestResult result, PerformanceMetrics metrics,
                                         BuyAndHoldBenchmark benchmark) {
        StringBuilder sb = new StringBuilder();

        sb.append("symbol=").append(result.symbol()).append('\n');
        sb.append("config=").append(configText(result.config())).append('\n');
        sb.append("firstEvaluableDate=").append(result.firstEvaluableDate().map(LocalDate::toString).orElse("EMPTY"))
                .append('\n');

        sb.append("equityCurve(").append(result.equityCurve().size()).append(")=\n");
        for (EquityPoint p : result.equityCurve()) {
            sb.append("  ").append(p.date()).append(';').append(p.cash().toPlainString()).append(';')
                    .append(p.quantity()).append(';').append(p.costBasis().toPlainString()).append(';')
                    .append(p.realizedPnl().toPlainString()).append(';').append(p.close().toPlainString())
                    .append('\n');
        }

        sb.append("fills(").append(result.fills().size()).append(")=\n");
        for (Fill f : result.fills()) {
            sb.append("  ").append(f.orderId()).append(';').append(f.side()).append(';').append(f.date()).append(';')
                    .append(f.quantity()).append(';').append(f.referenceOpen().toPlainString()).append(';')
                    .append(f.fillPrice().toPlainString()).append(';').append(f.commission().toPlainString())
                    .append(';').append(f.slippageCost().toPlainString()).append(';')
                    .append(signalText(f.signal())).append('\n');
        }

        sb.append("rejections(").append(result.rejections().size()).append(")=\n");
        for (OrderRejection r : result.rejections()) {
            sb.append("  ").append(rejectionText(r)).append('\n');
        }

        sb.append("trades(").append(result.trades().size()).append(")=\n");
        for (Trade t : result.trades()) {
            sb.append("  ").append(tradeText(t)).append('\n');
        }

        sb.append("totalCommission=").append(result.totalCommission().toPlainString()).append('\n');
        sb.append("totalSlippageCost=").append(result.totalSlippageCost().toPlainString()).append('\n');

        sb.append("metrics.totalReturn=").append(bits(metrics.totalReturn())).append('\n');
        sb.append("metrics.cagr=").append(bits(metrics.cagr())).append('\n');
        sb.append("metrics.volatility=").append(bits(metrics.volatility())).append('\n');
        sb.append("metrics.sharpeRatio=").append(bits(metrics.sharpeRatio())).append('\n');
        sb.append("metrics.maxDrawdown=").append(bits(metrics.maxDrawdown())).append('\n');
        sb.append("metrics.closedTradeCount=").append(metrics.closedTradeCount()).append('\n');
        sb.append("metrics.winRate=").append(bits(metrics.winRate())).append('\n');
        sb.append("metrics.averageWin=").append(bits(metrics.averageWin())).append('\n');
        sb.append("metrics.averageLoss=").append(bits(metrics.averageLoss())).append('\n');

        EquityPoint benchRef = benchmark.equityCurve().get(0);
        sb.append("benchmark.initialCapital=").append(benchmark.initialCapital().toPlainString()).append('\n');
        sb.append("benchmark.cash=").append(benchRef.cash().toPlainString()).append('\n');
        sb.append("benchmark.quantity=").append(benchRef.quantity()).append('\n');
        sb.append("benchmark.costBasis=").append(benchRef.costBasis().toPlainString()).append('\n');
        sb.append("benchmark.totalReturn=").append(bits(benchmark.totalReturn())).append('\n');

        return sb.toString();
    }

    private static String configText(BacktestConfig c) {
        return c.initialCapital().toPlainString() + ';' + c.commissionPerFill().toPlainString() + ';'
                + c.slippageRate().toPlainString() + ';' + c.startDate() + ';' + c.endDate();
    }

    private static String signalText(SignalEvent signal) {
        return signal.type() + "@" + signal.date() + ";close=" + signal.snapshot().close().toPlainString();
    }

    private static String rejectionText(OrderRejection r) {
        return switch (r) {
            case OrderRejection.ZeroQuantity zq ->
                    "ZERO_QUANTITY;" + zq.date() + ';' + signalText(zq.signal());
            case OrderRejection.InsufficientCash ic ->
                    "INSUFFICIENT_CASH;" + ic.orderId() + ';' + ic.date() + ';' + ic.quantity() + ';'
                            + ic.requiredCash().toPlainString() + ';' + ic.availableCash().toPlainString() + ';'
                            + signalText(ic.signal());
        };
    }

    private static String tradeText(Trade t) {
        return switch (t) {
            case Trade.Open o -> "OPEN;entryOrderId=" + o.entry().orderId() + ";quantity=" + o.quantity();
            case Trade.Closed c -> "CLOSED;entryOrderId=" + c.entry().orderId() + ";exitOrderId="
                    + c.exit().orderId() + ";quantity=" + c.quantity() + ";realizedPnl="
                    + c.realizedPnl().toPlainString() + ";totalCommission=" + c.totalCommission().toPlainString()
                    + ";totalSlippageCost=" + c.totalSlippageCost().toPlainString();
        };
    }

    private static String bits(double d) {
        return Long.toHexString(Double.doubleToRawLongBits(d));
    }

    private static String bits(OptionalDouble d) {
        return d.isPresent() ? bits(d.getAsDouble()) : "EMPTY";
    }

    private static final String EXPECTED_FINGERPRINT = """
            symbol=FINGERPRINT
            config=10000;1;0.001;2020-01-01;2021-02-17
            firstEvaluableDate=2020-03-04
            equityCurve(60)=
              2020-01-01;10000;0;0;0;100.00
              2020-01-08;10000;0;0;0;101.40
              2020-01-15;10000;0;0;0;102.80
              2020-01-22;10000;0;0;0;104.20
              2020-01-29;10000;0;0;0;105.60
              2020-02-05;10000;0;0;0;107.00
              2020-02-12;10000;0;0;0;108.40
              2020-02-19;10000;0;0;0;107.80
              2020-02-26;10000;0;0;0;107.20
              2020-03-04;10000;0;0;0;106.60
              2020-03-11;131.14200;93;9868.85800;0;106.00
              2020-03-18;131.14200;93;9868.85800;0;105.40
              2020-03-25;9866.79560;0;0;-133.20440;104.80
              2020-04-01;9866.79560;0;0;-133.20440;106.20
              2020-04-08;9866.79560;0;0;-133.20440;107.60
              2020-04-15;9866.79560;0;0;-133.20440;109.00
              2020-04-22;9866.79560;0;0;-133.20440;110.40
              2020-04-29;9866.79560;0;0;-133.20440;111.80
              2020-05-06;9866.79560;0;0;-133.20440;113.20
              2020-05-13;59.79940;87;9806.99620;-133.20440;112.60
              2020-05-20;59.79940;87;9806.99620;-133.20440;112.00
              2020-05-27;59.79940;87;9806.99620;-133.20440;111.40
              2020-06-03;59.79940;87;9806.99620;-133.20440;110.80
              2020-06-10;59.79940;87;9806.99620;-133.20440;110.20
              2020-06-17;9584.46420;0;0;-415.53580;109.60
              2020-06-24;9584.46420;0;0;-415.53580;111.00
              2020-07-01;9584.46420;0;0;-415.53580;112.40
              2020-07-08;9584.46420;0;0;-415.53580;113.80
              2020-07-15;9584.46420;0;0;-415.53580;115.20
              2020-07-22;9584.46420;0;0;-415.53580;116.60
              2020-07-29;9584.46420;0;0;-415.53580;118.00
              2020-08-05;64.55480;81;9519.90940;-415.53580;117.40
              2020-08-12;64.55480;81;9519.90940;-415.53580;116.80
              2020-08-19;64.55480;81;9519.90940;-415.53580;116.20
              2020-08-26;64.55480;81;9519.90940;-415.53580;115.60
              2020-09-02;64.55480;81;9519.90940;-415.53580;115.00
              2020-09-09;9320.68840;0;0;-679.31160;114.40
              2020-09-16;9320.68840;0;0;-679.31160;115.80
              2020-09-23;9320.68840;0;0;-679.31160;117.20
              2020-09-30;9320.68840;0;0;-679.31160;118.60
              2020-10-07;9320.68840;0;0;-679.31160;120.00
              2020-10-14;9320.68840;0;0;-679.31160;121.40
              2020-10-21;9320.68840;0;0;-679.31160;122.80
              2020-10-28;145.52340;75;9175.16500;-679.31160;122.20
              2020-11-04;145.52340;75;9175.16500;-679.31160;121.60
              2020-11-11;145.52340;75;9175.16500;-679.31160;121.00
              2020-11-18;145.52340;75;9175.16500;-679.31160;120.40
              2020-11-25;145.52340;75;9175.16500;-679.31160;119.80
              2020-12-02;9075.58340;0;0;-924.41660;119.20
              2020-12-09;9075.58340;0;0;-924.41660;120.60
              2020-12-16;9075.58340;0;0;-924.41660;122.00
              2020-12-23;9075.58340;0;0;-924.41660;123.40
              2020-12-30;9075.58340;0;0;-924.41660;124.80
              2021-01-06;9075.58340;0;0;-924.41660;126.20
              2021-01-13;5.92380;71;9069.65960;-924.41660;127.60
              2021-01-20;5.92380;71;9069.65960;-924.41660;127.00
              2021-01-27;5.92380;71;9069.65960;-924.41660;126.40
              2021-02-03;5.92380;71;9069.65960;-924.41660;125.80
              2021-02-10;5.92380;71;9069.65960;-924.41660;125.20
              2021-02-17;5.92380;71;9069.65960;-924.41660;124.60
            fills(9)=
              1;BUY;2020-03-11;93;106.00;106.10600;1;9.85800;ENTER@2020-03-04;close=106.60
              2;SELL;2020-03-25;93;104.80;104.69520;1;9.74640;EXIT@2020-03-18;close=105.40
              7;BUY;2020-05-13;87;112.60;112.71260;1;9.79620;ENTER@2020-05-06;close=113.20
              8;SELL;2020-06-17;87;109.60;109.49040;1;9.53520;EXIT@2020-06-10;close=110.20
              13;BUY;2020-08-05;81;117.40;117.51740;1;9.50940;ENTER@2020-07-29;close=118.00
              14;SELL;2020-09-09;81;114.40;114.28560;1;9.26640;EXIT@2020-09-02;close=115.00
              19;BUY;2020-10-28;75;122.20;122.32220;1;9.16500;ENTER@2020-10-21;close=122.80
              20;SELL;2020-12-02;75;119.20;119.08080;1;8.94000;EXIT@2020-11-25;close=119.80
              24;BUY;2021-01-13;71;127.60;127.72760;1;9.05960;ENTER@2021-01-06;close=126.20
            rejections(15)=
              INSUFFICIENT_CASH;3;2020-04-15;91;9930.91900;9866.79560;ENTER@2020-04-08;close=107.60
              INSUFFICIENT_CASH;4;2020-04-22;90;9947.93600;9866.79560;ENTER@2020-04-15;close=109.00
              INSUFFICIENT_CASH;5;2020-04-29;89;9962.15020;9866.79560;ENTER@2020-04-22;close=110.40
              INSUFFICIENT_CASH;6;2020-05-06;88;9973.56160;9866.79560;ENTER@2020-04-29;close=111.80
              INSUFFICIENT_CASH;9;2020-07-08;85;9684.67300;9584.46420;ENTER@2020-07-01;close=112.40
              INSUFFICIENT_CASH;10;2020-07-15;84;9688.47680;9584.46420;ENTER@2020-07-08;close=113.80
              INSUFFICIENT_CASH;11;2020-07-22;83;9689.47780;9584.46420;ENTER@2020-07-15;close=115.20
              INSUFFICIENT_CASH;12;2020-07-29;82;9687.67600;9584.46420;ENTER@2020-07-22;close=116.60
              INSUFFICIENT_CASH;15;2020-09-30;79;9380.76940;9320.68840;ENTER@2020-09-23;close=117.20
              INSUFFICIENT_CASH;16;2020-10-07;78;9371.36000;9320.68840;ENTER@2020-09-30;close=118.60
              INSUFFICIENT_CASH;17;2020-10-14;77;9359.14780;9320.68840;ENTER@2020-10-07;close=120.00
              INSUFFICIENT_CASH;18;2020-10-21;76;9344.13280;9320.68840;ENTER@2020-10-14;close=121.40
              INSUFFICIENT_CASH;21;2020-12-23;74;9142.73160;9075.58340;ENTER@2020-12-16;close=122.00
              INSUFFICIENT_CASH;22;2020-12-30;73;9121.51040;9075.58340;ENTER@2020-12-23;close=123.40
              INSUFFICIENT_CASH;23;2021-01-06;72;9097.48640;9075.58340;ENTER@2020-12-30;close=124.80
            trades(5)=
              CLOSED;entryOrderId=1;exitOrderId=2;quantity=93;realizedPnl=-133.20440;totalCommission=2;totalSlippageCost=19.60440
              CLOSED;entryOrderId=7;exitOrderId=8;quantity=87;realizedPnl=-282.33140;totalCommission=2;totalSlippageCost=19.33140
              CLOSED;entryOrderId=13;exitOrderId=14;quantity=81;realizedPnl=-263.77580;totalCommission=2;totalSlippageCost=18.77580
              CLOSED;entryOrderId=19;exitOrderId=20;quantity=75;realizedPnl=-245.10500;totalCommission=2;totalSlippageCost=18.10500
              OPEN;entryOrderId=24;quantity=71
            totalCommission=9
            totalSlippageCost=84.87620
            metrics.totalReturn=bfbd60199b319f35
            metrics.cagr=bfba246d1d42ceb8
            metrics.volatility=3fa4ae4e2d24a7dc
            metrics.sharpeRatio=c029b5bac3f0c40d
            metrics.maxDrawdown=3fbd60199b319f35
            metrics.closedTradeCount=4
            metrics.winRate=0
            metrics.averageWin=EMPTY
            metrics.averageLoss=c06ce35532617c1c
            benchmark.initialCapital=10000
            benchmark.cash=89.10000
            benchmark.quantity=99
            benchmark.costBasis=9910.90000
            benchmark.totalReturn=3fcf089a02752546
            """;
}
