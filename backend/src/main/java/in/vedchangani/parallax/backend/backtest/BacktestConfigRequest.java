package in.vedchangani.parallax.backend.backtest;

/**
 * The {@code config} sub-object of the D-34 create-run request envelope
 * (Batch 2 DTO layer; REST wiring is Batch 3): {@code initialCapital},
 * {@code commissionPerFill} and {@code slippageRate} are JSON strings under
 * the D-30 decimal grammar — never JSON numbers, exactly like {@code
 * Constant}/{@code CashFraction} (D-30) — so {@link BacktestConfigMapper}
 * can parse them straight into {@link java.math.BigDecimal} without ever
 * routing a monetary value through {@code double}. {@code startDate}/
 * {@code endDate} are ISO-8601 date strings ({@code yyyy-MM-dd}).
 *
 * <p>This record carries no Bean Validation: every field is parsed and
 * validated by {@link BacktestConfigMapper}, which distinguishes malformed
 * syntax ({@link MalformedBacktestConfigException}) from a semantically
 * invalid engine {@code BacktestConfig} ({@link InvalidBacktestConfigException}).
 */
public record BacktestConfigRequest(String initialCapital, String commissionPerFill, String slippageRate,
                                     String startDate, String endDate) {
}
