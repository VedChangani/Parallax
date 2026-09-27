package in.vedchangani.parallax.backend.dataset;

import in.vedchangani.parallax.engine.data.Bar;
import in.vedchangani.parallax.engine.data.BarSeries;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * The D-32 content-hash contract for a validated {@link BarSeries}: what a
 * {@link DatasetVersion} actually stores as {@code barCount}/{@code
 * firstDate}/{@code lastDate}/{@code contentHash}, and how those four
 * values are (re)computed and verified.
 *
 * <p>The hash is computed from the normalized, already-{@link Bar}-validated
 * {@code BarSeries} — never from raw CSV bytes. Byte-level differences that
 * produce the same normalized series (a leading BOM, CRLF vs. LF, an
 * optional final newline, {@code "100.0"} vs {@code "100"}) therefore never
 * change the hash. {@link #of(BarSeries)} rejects a series containing any
 * non-canonical price rather than silently normalizing it — the hash is
 * only ever defined over canonical data.
 *
 * <p>Canonical payload (UTF-8), one line per component, every line
 * (including the last bar line) ending in a single LF:
 *
 * <pre>{@code
 * PARALLAX-BARS/1
 * <symbol>
 * <date>,<open>,<high>,<low>,<close>,<volume>
 * ...
 * }</pre>
 *
 * where {@code date} is {@link LocalDate#toString()}, each price is the
 * canonical {@link BigDecimal#toPlainString()}, and {@code volume} is
 * {@link Long#toString(long)}. The hash is SHA-256 of that payload, encoded
 * as lowercase hexadecimal (64 characters).
 *
 * <p>What the hash covers: the format tag, the symbol, and every bar, in
 * order. What it excludes: dataset name, source, {@code sourceDetail},
 * {@code adjustmentBasis}, timestamps, and every database id — the hash
 * identifies market data, not upload metadata.
 *
 * <p>SHA-256 stays a private implementation detail here rather than a
 * shared utility: the only other user, D-30's {@code
 * StrategyDefinitionCodec}, hashes a completely different payload shape
 * (canonical JSON, not a bars listing), and the two share nothing but the
 * JDK {@link MessageDigest} call. Duplicating six lines is cheaper than a
 * shared package for that.
 */
public record DatasetContent(String contentHash, int barCount, LocalDate firstDate, LocalDate lastDate) {

    private static final String FORMAT_TAG = "PARALLAX-BARS/1";

    public DatasetContent {
        Objects.requireNonNull(contentHash, "contentHash must not be null");
        Objects.requireNonNull(firstDate, "firstDate must not be null");
        Objects.requireNonNull(lastDate, "lastDate must not be null");
        if (barCount < 1) {
            throw new IllegalArgumentException("barCount must be >= 1, was " + barCount);
        }
    }

    /**
     * Canonicalizes a price the same way {@code BacktestConfig}/{@code
     * CashFraction} canonicalize a {@link BigDecimal} in the engine
     * (unmodified there; this is a backend-local copy of the same rule,
     * not a shared helper — the engine is never touched by D-32):
     * {@code stripTrailingZeros()}, then {@code setScale(0)} if the
     * resulting scale is negative. The numeric value is never changed —
     * only its representation. Applies to prices only, never dates or
     * volume.
     */
    public static BigDecimal canonicalPrice(BigDecimal value) {
        Objects.requireNonNull(value, "value must not be null");
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    /**
     * Computes the content hash and derived metadata for an already-built,
     * already-{@link Bar}-validated {@link BarSeries}.
     *
     * @throws IllegalArgumentException if any bar's O/H/L/C is not already
     *                                   in canonical form (see {@link
     *                                   #canonicalPrice(BigDecimal)}) — this
     *                                   method never silently normalizes
     */
    public static DatasetContent of(BarSeries series) {
        Objects.requireNonNull(series, "series must not be null");
        List<Bar> bars = series.bars();
        requireCanonical(bars);

        String payload = payloadOf(series.symbol(), bars);
        String hash = sha256Hex(payload.getBytes(StandardCharsets.UTF_8));

        LocalDate first = bars.get(0).date();
        LocalDate last = bars.get(bars.size() - 1).date();
        return new DatasetContent(hash, bars.size(), first, last);
    }

    /**
     * Recomputes {@link #of(BarSeries)} over {@code series} and verifies it
     * matches every one of the previously stored values exactly. Used when
     * reconstructing a {@link DatasetVersion}'s bars for any future
     * consumer (D-32's own {@code getVerifiedSeries}, and every later
     * backtest run) — a mismatch means stored data was corrupted or
     * tampered with, and is treated as an integrity failure, never
     * silently repaired.
     *
     * @throws DatasetIntegrityException on any mismatch, including a
     *                                    non-canonical stored price
     */
    public static void verify(BarSeries series, int expectedBarCount, LocalDate expectedFirstDate,
                               LocalDate expectedLastDate, String expectedContentHash) {
        Objects.requireNonNull(series, "series must not be null");
        Objects.requireNonNull(expectedFirstDate, "expectedFirstDate must not be null");
        Objects.requireNonNull(expectedLastDate, "expectedLastDate must not be null");
        Objects.requireNonNull(expectedContentHash, "expectedContentHash must not be null");

        DatasetContent recomputed;
        try {
            recomputed = of(series);
        } catch (IllegalArgumentException e) {
            throw new DatasetIntegrityException("stored bar data is not in canonical form: " + e.getMessage(), e);
        }

        if (recomputed.barCount() != expectedBarCount) {
            throw new DatasetIntegrityException("bar count mismatch: expected " + expectedBarCount
                    + " but reconstructed series has " + recomputed.barCount());
        }
        if (!recomputed.firstDate().equals(expectedFirstDate)) {
            throw new DatasetIntegrityException("first date mismatch: expected " + expectedFirstDate
                    + " but reconstructed series starts " + recomputed.firstDate());
        }
        if (!recomputed.lastDate().equals(expectedLastDate)) {
            throw new DatasetIntegrityException("last date mismatch: expected " + expectedLastDate
                    + " but reconstructed series ends " + recomputed.lastDate());
        }
        if (!recomputed.contentHash().equals(expectedContentHash)) {
            throw new DatasetIntegrityException(
                    "content hash mismatch: expected " + expectedContentHash + " but recomputed "
                            + recomputed.contentHash());
        }
    }

    private static void requireCanonical(List<Bar> bars) {
        for (Bar bar : bars) {
            requireCanonical(bar.open(), bar.date(), "open");
            requireCanonical(bar.high(), bar.date(), "high");
            requireCanonical(bar.low(), bar.date(), "low");
            requireCanonical(bar.close(), bar.date(), "close");
        }
    }

    private static void requireCanonical(BigDecimal value, LocalDate date, String field) {
        if (!value.equals(canonicalPrice(value))) {
            throw new IllegalArgumentException(
                    "%s on %s is not canonical: %s".formatted(field, date, value));
        }
    }

    private static String payloadOf(String symbol, List<Bar> bars) {
        StringBuilder sb = new StringBuilder();
        sb.append(FORMAT_TAG).append('\n');
        sb.append(symbol).append('\n');
        for (Bar bar : bars) {
            sb.append(bar.date()).append(',')
                    .append(bar.open().toPlainString()).append(',')
                    .append(bar.high().toPlainString()).append(',')
                    .append(bar.low().toPlainString()).append(',')
                    .append(bar.close().toPlainString()).append(',')
                    .append(bar.volume())
                    .append('\n');
        }
        return sb.toString();
    }

    private static String sha256Hex(byte[] payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandatory JDK algorithm (JLS platform guarantee); unreachable.
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
