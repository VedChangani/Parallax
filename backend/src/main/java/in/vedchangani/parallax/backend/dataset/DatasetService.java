package in.vedchangani.parallax.backend.dataset;

import in.vedchangani.parallax.backend.dataset.csv.CsvBarParser;
import in.vedchangani.parallax.backend.user.UserId;
import in.vedchangani.parallax.engine.data.Bar;
import in.vedchangani.parallax.engine.data.BarSeries;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;

/**
 * The only write path to {@link Dataset}/{@link DatasetVersion}/{@code
 * dataset_bar} (D-32, mirroring D-31's {@code StrategyService}). Every
 * operation is owner-scoped: the owner always comes from the caller's
 * {@link UserId}, never from a request body.
 *
 * <p><strong>Transactions.</strong> {@link #createVersionFromCsv} uses two
 * explicit {@link TransactionTemplate}s rather than {@code @Transactional}:
 * a short read-only one to resolve the dataset's immutable symbol, and a
 * short write one for the lock/allocate/insert sequence — so that CSV
 * parsing, {@code BarSeries} construction, and content hashing (real CPU
 * work, no database access) visibly happen between them, never while a row
 * lock is held. Both are pinned to {@code READ COMMITTED} for the same
 * reason D-31 pins its own: {@link #createVersionFromCsv}'s locking
 * algorithm depends on a {@code SELECT ... FOR UPDATE} re-reading the
 * latest committed row after waiting on the lock. Every other method uses
 * {@code @Transactional} directly.
 *
 * <p><strong>Every write to a {@link Dataset} row goes through {@link
 * DatasetRepository#lockByIdAndOwnerId}</strong> — including version-number
 * allocation — never only {@link DatasetRepository#findByIdAndOwnerId}.
 */
@Service
public class DatasetService {

    private final DatasetRepository datasetRepository;
    private final DatasetVersionRepository versionRepository;
    private final DatasetBarRepository barRepository;
    private final TransactionTemplate writeTransactionTemplate;
    private final TransactionTemplate readOnlyTransactionTemplate;

    public DatasetService(DatasetRepository datasetRepository, DatasetVersionRepository versionRepository,
                           DatasetBarRepository barRepository, PlatformTransactionManager transactionManager) {
        this.datasetRepository = datasetRepository;
        this.versionRepository = versionRepository;
        this.barRepository = barRepository;

        this.writeTransactionTemplate = new TransactionTemplate(transactionManager);
        this.writeTransactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

        // A separate template (not a mutated shared one) for the read-only
        // pre-check in createVersionFromCsv — using TransactionTemplate
        // rather than an internally self-invoked @Transactional method
        // sidesteps Spring's proxy self-invocation limitation entirely.
        this.readOnlyTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.readOnlyTransactionTemplate.setReadOnly(true);
    }

    /**
     * Creates a brand-new {@link Dataset} with no versions yet ({@code
     * latestVersionNumber == 0}). No lock is required: the new row is
     * invisible to any other transaction until commit, so there is nothing
     * to race except the {@code (owner, name)} uniqueness constraint, which
     * the database itself decides.
     */
    @Transactional
    public DatasetSummary createDataset(UserId owner, String name, String symbol) {
        Objects.requireNonNull(owner, "owner must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(symbol, "symbol must not be null");

        Dataset dataset = new Dataset(owner.value(), name, symbol);
        Dataset saved = saveDatasetOrThrowDuplicate(dataset, name);
        return DatasetSummary.of(saved);
    }

    @Transactional(readOnly = true)
    public List<DatasetSummary> listDatasets(UserId owner) {
        Objects.requireNonNull(owner, "owner must not be null");
        return datasetRepository.findByOwnerIdOrderByIdAsc(owner.value()).stream()
                .map(DatasetSummary::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public DatasetSummary getDataset(UserId owner, long datasetId) {
        Objects.requireNonNull(owner, "owner must not be null");
        return DatasetSummary.of(loadOwned(owner, datasetId));
    }

    /**
     * Parses, validates, canonicalizes, and hashes {@code csv} entirely
     * before opening any transaction, then allocates {@code latest + 1}
     * under the owner-scoped row lock and inserts the immutable version
     * plus every bar row (mirroring D-31 §4). If anything in either
     * callback throws, that transaction — including a parent counter
     * increment — rolls back in full, so a failed upload never consumes a
     * version number and never leaves an orphan bar row.
     */
    public DatasetVersionSummary createVersionFromCsv(UserId owner, long datasetId, byte[] csv,
                                                       AdjustmentBasis basis, String sourceDetail) {
        Objects.requireNonNull(owner, "owner must not be null");
        Objects.requireNonNull(csv, "csv must not be null");
        Objects.requireNonNull(basis, "basis must not be null");
        Objects.requireNonNull(sourceDetail, "sourceDetail must not be null");

        // Outside any write transaction: resolve the dataset's immutable symbol first,
        // so a nonexistent/cross-owner dataset 404s before any parsing work happens.
        String symbol = readOnlyTransactionTemplate.execute(status -> loadOwned(owner, datasetId).symbol());

        // Outside any transaction: parse, validate, canonicalize, and hash. No database
        // access happens here — this is real CPU work that must never occur while a
        // row lock is held.
        List<Bar> bars = CsvBarParser.parse(csv);
        BarSeries series = new BarSeries(symbol, bars);
        DatasetContent content = DatasetContent.of(series);

        DatasetVersion version = writeTransactionTemplate.execute(status -> {
            Dataset dataset = datasetRepository.lockByIdAndOwnerId(datasetId, owner.value())
                    .orElseThrow(() -> new DatasetNotFoundException(datasetId));
            if (!dataset.symbol().equals(series.symbol())) {
                // Unreachable given Dataset.symbol's immutability (D-32) — surfaced as an
                // engine-bug-style failure rather than silently proceeding with a mismatch.
                throw new IllegalStateException("dataset symbol changed between read and lock: "
                        + dataset.symbol() + " vs " + series.symbol());
            }
            int nextVersionNumber = dataset.allocateNextVersionNumber();
            datasetRepository.saveAndFlush(dataset);

            DatasetVersion created = new DatasetVersion(dataset.id(), nextVersionNumber, dataset.symbol(),
                    DatasetSource.CSV_UPLOAD, sourceDetail, basis, content);
            DatasetVersion saved = saveVersionOrThrowConflict(created, datasetId);

            barRepository.insertAll(saved.id(), bars);
            return saved;
        });

        return DatasetVersionSummary.of(version);
    }

    @Transactional(readOnly = true)
    public List<DatasetVersionSummary> listVersions(UserId owner, long datasetId) {
        Objects.requireNonNull(owner, "owner must not be null");
        // Dataset existence/ownership is checked first, so a nonexistent or cross-owner
        // dataset id 404s rather than silently returning an empty list.
        loadOwned(owner, datasetId);
        return versionRepository.findAllOwned(datasetId, owner.value()).stream()
                .map(DatasetVersionSummary::of)
                .toList();
    }

    /**
     * Metadata only — no bars are read, so no integrity verification runs
     * here (see {@link #getVerifiedSeries}).
     */
    @Transactional(readOnly = true)
    public DatasetVersionSummary getVersion(UserId owner, long datasetId, int versionNumber) {
        Objects.requireNonNull(owner, "owner must not be null");
        DatasetVersion version = versionRepository.findOwned(datasetId, owner.value(), versionNumber)
                .orElseThrow(() -> new DatasetVersionNotFoundException(datasetId, versionNumber));
        return DatasetVersionSummary.of(version);
    }

    /**
     * The only way a {@link BarSeries} leaves the {@code dataset} package
     * (D-32 §16): reconstructs it from stored {@code dataset_bar} rows and
     * verifies it against the stored {@code barCount}/{@code firstDate}/
     * {@code lastDate}/{@code contentHash} before returning it. Any
     * mismatch is an integrity failure, never repaired or resaved.
     */
    @Transactional(readOnly = true)
    public VerifiedDatasetVersion getVerifiedSeries(UserId owner, long datasetId, int versionNumber) {
        Objects.requireNonNull(owner, "owner must not be null");
        DatasetVersion version = versionRepository.findOwned(datasetId, owner.value(), versionNumber)
                .orElseThrow(() -> new DatasetVersionNotFoundException(datasetId, versionNumber));

        // Both calls can throw IllegalArgumentException for tampered/corrupted stored
        // data: barRepository.findOwned's row mapper constructs an engine Bar per row
        // (rejecting a non-positive price, an inconsistent high/low, or negative volume
        // there and then), and BarSeries re-validates the assembled sequence. Both are
        // wrapped identically as integrity failures; a genuine DataAccessException from
        // the JDBC call is a different exception hierarchy and is not caught here, so it
        // still surfaces as an ordinary database error.
        List<Bar> bars;
        BarSeries series;
        try {
            bars = barRepository.findOwned(version.id(), owner.value());
            series = new BarSeries(version.symbol(), bars);
        } catch (IllegalArgumentException e) {
            throw new DatasetIntegrityException("stored bars failed validation: " + e.getMessage(), e);
        }

        DatasetContent.verify(series, version.barCount(), version.firstDate(), version.lastDate(),
                version.contentHash());

        return new VerifiedDatasetVersion(DatasetVersionSummary.of(version), series);
    }

    // --- internal helpers ----------------------------------------------------

    private Dataset loadOwned(UserId owner, long datasetId) {
        return datasetRepository.findByIdAndOwnerId(datasetId, owner.value())
                .orElseThrow(() -> new DatasetNotFoundException(datasetId));
    }

    private Dataset saveDatasetOrThrowDuplicate(Dataset dataset, String name) {
        try {
            return datasetRepository.saveAndFlush(dataset);
        } catch (DataIntegrityViolationException e) {
            if (isConstraint(e, "uq_dataset_owner_name")) {
                throw new DuplicateDatasetNameException(name);
            }
            throw e;
        }
    }

    private DatasetVersion saveVersionOrThrowConflict(DatasetVersion version, long datasetId) {
        try {
            return versionRepository.saveAndFlush(version);
        } catch (DataIntegrityViolationException e) {
            if (isConstraint(e, "uq_dataset_version_number")) {
                throw new DatasetVersionConflictException(datasetId);
            }
            throw e;
        }
    }

    /**
     * Matches a failed write against a specific database constraint name,
     * never against SQL state or message text alone where a constraint
     * name is available (mirroring D-31's own helper exactly).
     */
    private static boolean isConstraint(DataIntegrityViolationException e, String constraintName) {
        Throwable cause = e.getCause();
        if (cause instanceof ConstraintViolationException cve && cve.getConstraintName() != null) {
            return cve.getConstraintName().equalsIgnoreCase(constraintName);
        }
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(constraintName);
    }
}
