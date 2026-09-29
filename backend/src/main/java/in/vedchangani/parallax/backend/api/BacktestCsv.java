package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.backtest.BacktestRunDetail;

import java.math.BigDecimal;
import java.util.List;

/**
 * CSV renderings of an already-verified {@link BacktestRunDetail} (V1.1
 * Batch 4, D-42). Pure formatting over the exact same response records the
 * JSON endpoints return ({@link BacktestEquityPointResponse}, {@link
 * BacktestTradeResponse}): no value is recomputed, re-rounded or reparsed
 * here, so a CSV cell is character-for-character the JSON API's string.
 *
 * <p>Format (RFC 4180): UTF-8 without BOM, comma separator, {@code CRLF}
 * after every record including the last, one header row, a field quoted
 * only when it contains a comma, double quote, CR or LF (embedded quotes
 * doubled). Dates are ISO-8601. Money is the backend's exact decimal
 * string. A value that does not apply (an open trade's exit, say) is an
 * empty field, never a placeholder. {@code drawdown} is a plain decimal
 * fraction {@code >= 0} (no exponent or trailing zeros), e.g. {@code 0.25} for
 * 25% below the running peak. Output depends only on the run, so it is
 * byte-for-byte deterministic.
 */
final class BacktestCsv {

    static final String EQUITY_CURVE_HEADER = "date,equity,cash,quantity,close,market_value,cost_basis,"
            + "realized_pnl,unrealized_pnl,benchmark_equity,drawdown";

    static final String TRADES_HEADER = "status,quantity,entry_order_id,entry_date,entry_price,entry_commission,"
            + "exit_order_id,exit_date,exit_price,exit_commission,realized_pnl,total_commission,"
            + "total_slippage_cost,mark_date,mark_close,market_value,unrealized_pnl";

    private static final String EOL = "\r\n";

    private BacktestCsv() {
    }

    static String equityCurve(BacktestRunDetail detail) {
        StringBuilder csv = new StringBuilder(EQUITY_CURVE_HEADER).append(EOL);
        for (BacktestEquityPointResponse point : BacktestEquityPointResponse.listOf(detail)) {
            record(csv, String.valueOf(point.date()), point.equity(), point.cash(), String.valueOf(point.quantity()),
                    point.close(), point.marketValue(), point.costBasis(), point.realizedPnl(),
                    point.unrealizedPnl(), point.benchmarkEquity(), plainDecimal(point.drawdown()));
        }
        return csv.toString();
    }

    static String trades(BacktestRunDetail detail) {
        StringBuilder csv = new StringBuilder(TRADES_HEADER).append(EOL);
        List<BacktestTradeResponse> trades =
                detail.trades().stream().map(trade -> BacktestTradeResponse.of(trade, detail)).toList();
        for (BacktestTradeResponse trade : trades) {
            BacktestFillResponse entry = trade.entry();
            BacktestFillResponse exit = trade.exit();
            record(csv, trade.status().name(), String.valueOf(trade.quantity()),
                    String.valueOf(entry.orderId()), String.valueOf(entry.date()), entry.fillPrice(),
                    entry.commission(),
                    exit == null ? null : String.valueOf(exit.orderId()),
                    exit == null ? null : String.valueOf(exit.date()),
                    exit == null ? null : exit.fillPrice(),
                    exit == null ? null : exit.commission(),
                    trade.realizedPnl(), trade.totalCommission(), trade.totalSlippageCost(),
                    trade.markDate() == null ? null : trade.markDate().toString(), trade.markClose(),
                    trade.marketValue(), trade.unrealizedPnl());
        }
        return csv.toString();
    }

    /**
     * The shortest decimal that round-trips the double, written without an
     * exponent or trailing zeros (so {@code 0.0} is {@code 0}).
     */
    static String plainDecimal(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    /** One RFC 4180 field; {@code null} is an empty field. */
    static String escape(String field) {
        if (field == null) {
            return "";
        }
        boolean needsQuotes = field.indexOf(',') >= 0 || field.indexOf('"') >= 0
                || field.indexOf('\r') >= 0 || field.indexOf('\n') >= 0;
        return needsQuotes ? '"' + field.replace("\"", "\"\"") + '"' : field;
    }

    private static void record(StringBuilder csv, String... fields) {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(escape(fields[i]));
        }
        csv.append(EOL);
    }
}
