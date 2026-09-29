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

    public static BigDecimal canonicalPrice(BigDecimal value) {
        Objects.requireNonNull(value, "value must not be null");
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

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
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
