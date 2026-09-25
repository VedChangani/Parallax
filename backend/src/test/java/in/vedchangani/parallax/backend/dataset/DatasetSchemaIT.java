package in.vedchangani.parallax.backend.dataset;

import in.vedchangani.parallax.backend.TestcontainersConfiguration;
import in.vedchangani.parallax.backend.strategy.TestUsers;
import in.vedchangani.parallax.backend.user.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * D-32 schema-level proof, against real PostgreSQL (Testcontainers): the
 * database-layer immutability triggers on {@code dataset_version}/{@code
 * dataset_bar}, every CHECK constraint, the composite foreign key tying a
 * version's symbol snapshot to its parent, exact {@code numeric} scale
 * round-tripping, and that tampered/corrupted stored data is detected on
 * read rather than silently accepted. Mirrors D-31's {@code
 * StrategySchemaIT}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DatasetSchemaIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatasetService datasetService;

    private UserId user() {
        return TestUsers.create(jdbcTemplate, "schema");
    }

    private long createDatasetId(UserId owner, String symbol) {
        return datasetService.createDataset(owner, DatasetFixtures.uniqueName(), symbol).id();
    }

    private long createDatasetAndVersionId(UserId owner) {
        long datasetId = createDatasetId(owner, "AAPL");
        datasetService.createVersionFromCsv(owner, datasetId, DatasetFixtures.simpleCsv(), AdjustmentBasis.RAW,
                "a.csv");
        return datasetId;
    }

    // --- immutability triggers -----------------------------------------------

    @Test
    void updatingADatasetVersionRowIsRejectedByTheDatabase() {
        long datasetId = createDatasetAndVersionId(user());
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "update dataset_version set version_number = 999 where dataset_id = ?", datasetId));
    }

    @Test
    void deletingADatasetVersionRowIsRejectedByTheDatabase() {
        long datasetId = createDatasetAndVersionId(user());
        assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update("delete from dataset_version where dataset_id = ?", datasetId));
    }

    @Test
    void truncatingDatasetVersionIsRejectedByTheDatabase() {
        createDatasetAndVersionId(user());
        assertThrows(DataAccessException.class, () -> jdbcTemplate.execute("truncate table dataset_version"));
    }

    @Test
    void updatingADatasetBarRowIsRejectedByTheDatabase() {
        long datasetId = createDatasetAndVersionId(user());
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "update dataset_bar set volume = 999 where dataset_version_id = "
                        + "(select id from dataset_version where dataset_id = ?)", datasetId));
    }

    @Test
    void deletingADatasetBarRowIsRejectedByTheDatabase() {
        long datasetId = createDatasetAndVersionId(user());
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "delete from dataset_bar where dataset_version_id = "
                        + "(select id from dataset_version where dataset_id = ?)", datasetId));
    }

    @Test
    void truncatingDatasetBarIsRejectedByTheDatabase() {
        createDatasetAndVersionId(user());
        assertThrows(DataAccessException.class, () -> jdbcTemplate.execute("truncate table dataset_bar"));
    }

    // --- CHECK constraints ----------------------------------------------------

    @Test
    void hashFormatCheckRejectsANonHexOrWrongLengthHash() {
        long datasetId = createDatasetId(user(), "AAPL");
        assertThrows(DataAccessException.class, () -> insertVersion(datasetId, 1, "AAPL", "RAW", "not-a-valid-hash"));
    }

    @Test
    void symbolFormatCheckRejectsALowercaseSymbol() {
        UserId owner = user();
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "insert into dataset (owner_id, name, symbol) values (?, ?, 'aapl')",
                owner.value(), DatasetFixtures.uniqueName()));
    }

    @Test
    void blankNameCheckRejectsABlankDatasetName() {
        UserId owner = user();
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "insert into dataset (owner_id, name, symbol) values (?, '   ', 'AAPL')", owner.value()));
    }

    @Test
    void sourceCheckRejectsAnUnknownSource() {
        long datasetId = createDatasetId(user(), "AAPL");
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                insert into dataset_version
                    (dataset_id, version_number, symbol, source, source_detail, adjustment_basis,
                     bar_count, first_date, last_date, content_hash)
                values (?, 1, 'AAPL', 'ALPHA_VANTAGE', 'x', 'RAW', 1, '2024-01-02', '2024-01-02', ?)
                """, datasetId, "0".repeat(64)));
    }

    @Test
    void adjustmentBasisCheckRejectsAnUnknownValue() {
        long datasetId = createDatasetId(user(), "AAPL");
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                insert into dataset_version
                    (dataset_id, version_number, symbol, source, source_detail, adjustment_basis,
                     bar_count, first_date, last_date, content_hash)
                values (?, 1, 'AAPL', 'CSV_UPLOAD', 'x', 'ADJUSTED_SOMEHOW', 1, '2024-01-02', '2024-01-02', ?)
                """, datasetId, "0".repeat(64)));
    }

    @Test
    void barCountCheckRejectsZeroOrNegative() {
        long datasetId = createDatasetId(user(), "AAPL");
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                insert into dataset_version
                    (dataset_id, version_number, symbol, source, source_detail, adjustment_basis,
                     bar_count, first_date, last_date, content_hash)
                values (?, 1, 'AAPL', 'CSV_UPLOAD', 'x', 'RAW', 0, '2024-01-02', '2024-01-02', ?)
                """, datasetId, "0".repeat(64)));
    }

    @Test
    void dateRangeCheckRejectsFirstDateAfterLastDate() {
        long datasetId = createDatasetId(user(), "AAPL");
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
                insert into dataset_version
                    (dataset_id, version_number, symbol, source, source_detail, adjustment_basis,
                     bar_count, first_date, last_date, content_hash)
                values (?, 1, 'AAPL', 'CSV_UPLOAD', 'x', 'RAW', 1, '2024-01-05', '2024-01-02', ?)
                """, datasetId, "0".repeat(64)));
    }

    // --- composite foreign key --------------------------------------------------

    @Test
    void compositeForeignKeyRejectsAVersionWhoseSymbolDiffersFromItsDataset() {
        long datasetId = createDatasetId(user(), "AAPL");
        assertThrows(DataAccessException.class, () -> insertVersion(datasetId, 1, "MSFT", "RAW", "0".repeat(64)));
    }

    // --- numeric scale --------------------------------------------------------

    @Test
    void numericColumnsRoundTripExactScale() {
        UserId owner = user();
        long datasetId = createDatasetId(owner, "AAPL");
        long versionId = insertVersion(datasetId, 1, "AAPL", "RAW", "0".repeat(64));
        jdbcTemplate.update(
                "insert into dataset_bar (dataset_version_id, bar_date, open, high, low, close, volume) "
                        + "values (?, '2024-01-02', 185.64, 190.1, 180, 189.00000, 12345)",
                versionId);

        BigDecimal open = jdbcTemplate.queryForObject(
                "select open from dataset_bar where dataset_version_id = ?", BigDecimal.class, versionId);
        BigDecimal close = jdbcTemplate.queryForObject(
                "select close from dataset_bar where dataset_version_id = ?", BigDecimal.class, versionId);

        assertEquals(new BigDecimal("185.64"), open);
        assertEquals(2, open.scale());
        assertEquals(new BigDecimal("189.00000"), close);
        assertEquals(5, close.scale());
    }

    // --- integrity detection on read --------------------------------------------

    @Test
    void anExtraTamperedBarRowIsDetectedOnRead() {
        UserId owner = user();
        long datasetId = createDatasetId(owner, "AAPL");
        datasetService.createVersionFromCsv(owner, datasetId, DatasetFixtures.simpleCsv(), AdjustmentBasis.RAW,
                "a.csv");
        long versionId = jdbcTemplate.queryForObject(
                "select id from dataset_version where dataset_id = ? and version_number = 1", Long.class, datasetId);

        // Tamper directly: insert a third bar the stored barCount/hash metadata does not account for.
        jdbcTemplate.update(
                "insert into dataset_bar (dataset_version_id, bar_date, open, high, low, close, volume) "
                        + "values (?, '2024-01-04', 108, 115, 107, 112, 900)", versionId);

        assertThrows(DatasetIntegrityException.class, () -> datasetService.getVerifiedSeries(owner, datasetId, 1));
    }

    @Test
    void aWrongStoredHashIsDetectedOnRead() {
        UserId owner = user();
        long datasetId = createDatasetId(owner, "AAPL");
        long versionId = insertVersion(datasetId, 1, "AAPL", "RAW", "0".repeat(64));
        jdbcTemplate.update(
                "insert into dataset_bar (dataset_version_id, bar_date, open, high, low, close, volume) "
                        + "values (?, '2024-01-02', 100, 105, 99, 104, 1000)", versionId);

        assertThrows(DatasetIntegrityException.class, () -> datasetService.getVerifiedSeries(owner, datasetId, 1));
    }

    @Test
    void aNonCanonicalStoredPriceIsDetectedOnRead() {
        UserId owner = user();
        long datasetId = createDatasetId(owner, "AAPL");
        long versionId = insertVersion(datasetId, 1, "AAPL", "RAW", "0".repeat(64));
        // 100.00 (scale 2) is not canonical (canonicalPrice would strip it to 100, scale 0).
        jdbcTemplate.update(
                "insert into dataset_bar (dataset_version_id, bar_date, open, high, low, close, volume) "
                        + "values (?, '2024-01-02', 100.00, 105, 99, 104, 1000)", versionId);

        assertThrows(DatasetIntegrityException.class, () -> datasetService.getVerifiedSeries(owner, datasetId, 1));
    }

    /**
     * Corrective-pass regression test: {@code DatasetBarRepository}'s row
     * mapper constructs an engine {@code Bar} per row, so a stored row
     * with a non-positive price must surface as {@link
     * DatasetIntegrityException} from {@code getVerifiedSeries} — never
     * escape as the raw {@code IllegalArgumentException} {@code Bar}
     * itself throws. Nothing is repaired or resaved: the tampered row is
     * still readable, unchanged, afterward.
     */
    @Test
    void aTamperedNonPositivePriceCausesIntegrityExceptionNotARawException() {
        UserId owner = user();
        long datasetId = createDatasetId(owner, "AAPL");
        long versionId = insertVersion(datasetId, 1, "AAPL", "RAW", "0".repeat(64));
        jdbcTemplate.update(
                "insert into dataset_bar (dataset_version_id, bar_date, open, high, low, close, volume) "
                        + "values (?, '2024-01-02', 0, 105, 99, 104, 1000)", versionId);

        assertThrows(DatasetIntegrityException.class, () -> datasetService.getVerifiedSeries(owner, datasetId, 1));

        BigDecimal stillZero = jdbcTemplate.queryForObject(
                "select open from dataset_bar where dataset_version_id = ?", BigDecimal.class, versionId);
        assertEquals(new BigDecimal("0"), stillZero);
    }

    /**
     * Same regression, for an invalid OHLC relationship rather than a
     * non-positive price — a different {@code Bar} validation rule
     * reaching the same {@link DatasetIntegrityException} boundary.
     */
    @Test
    void aTamperedInvalidOhlcRelationshipCausesIntegrityException() {
        UserId owner = user();
        long datasetId = createDatasetId(owner, "AAPL");
        long versionId = insertVersion(datasetId, 1, "AAPL", "RAW", "0".repeat(64));
        // high (99) is below max(open, close) = 104 — Bar rejects this.
        jdbcTemplate.update(
                "insert into dataset_bar (dataset_version_id, bar_date, open, high, low, close, volume) "
                        + "values (?, '2024-01-02', 100, 99, 90, 104, 1000)", versionId);

        assertThrows(DatasetIntegrityException.class, () -> datasetService.getVerifiedSeries(owner, datasetId, 1));
    }

    private long insertVersion(long datasetId, int versionNumber, String symbol, String adjustmentBasis,
                                String contentHash) {
        return jdbcTemplate.queryForObject("""
                insert into dataset_version
                    (dataset_id, version_number, symbol, source, source_detail, adjustment_basis,
                     bar_count, first_date, last_date, content_hash)
                values (?, ?, ?, 'CSV_UPLOAD', 'manual.csv', ?, 1, '2024-01-02', '2024-01-02', ?)
                returning id
                """, Long.class, datasetId, versionNumber, symbol, adjustmentBasis, contentHash);
    }
}
