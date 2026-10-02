package in.vedchangani.parallax.backend.dataset;

import in.vedchangani.parallax.backend.dataset.csv.CsvBarParser;
import in.vedchangani.parallax.backend.marketdata.DailyBars;
import in.vedchangani.parallax.backend.marketdata.HistoryDepth;
import in.vedchangani.parallax.backend.marketdata.MarketDataProvider;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class DatasetService {

    private final DatasetRepository datasetRepository;
    private final DatasetVersionRepository versionRepository;
    private final DatasetBarRepository barRepository;
    private final MarketDataProvider marketDataProvider;
    private final TransactionTemplate writeTransactionTemplate;
    private final TransactionTemplate readOnlyTransactionTemplate;

    public DatasetService(DatasetRepository datasetRepository, DatasetVersionRepository versionRepository,
                           DatasetBarRepository barRepository, PlatformTransactionManager transactionManager,
                           MarketDataProvider marketDataProvider) {
        this.datasetRepository = datasetRepository;
        this.versionRepository = versionRepository;
        this.barRepository = barRepository;
        this.marketDataProvider = marketDataProvider;

        this.writeTransactionTemplate = new TransactionTemplate(transactionManager);
        this.writeTransactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

        this.readOnlyTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.readOnlyTransactionTemplate.setReadOnly(true);
    }

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

    public DatasetVersionSummary createVersionFromCsv(UserId owner, long datasetId, byte[] csv,
                                                       AdjustmentBasis basis, String sourceDetail) {
        Objects.requireNonNull(owner, "owner must not be null");
        Objects.requireNonNull(csv, "csv must not be null");
        Objects.requireNonNull(basis, "basis must not be null");
        Objects.requireNonNull(sourceDetail, "sourceDetail must not be null");

        String symbol = readOnlyTransactionTemplate.execute(status -> loadOwned(owner, datasetId).symbol());

        List<Bar> bars = CsvBarParser.parse(csv);
        BarSeries series = new BarSeries(symbol, bars);
        DatasetContent content = DatasetContent.of(series);

        DatasetVersion version = persistVersion(owner, datasetId, series, content, DatasetSource.CSV_UPLOAD,
                sourceDetail, basis, bars);

        return DatasetVersionSummary.of(version);
    }

    public DatasetVersionSummary createVersionFromAlphaVantage(UserId owner, long datasetId, HistoryDepth depth) {
        Objects.requireNonNull(owner, "owner must not be null");
        Objects.requireNonNull(depth, "depth must not be null");

        String symbol = readOnlyTransactionTemplate.execute(status -> loadOwned(owner, datasetId).symbol());

        DailyBars daily = marketDataProvider.fetchDailyBars(symbol, depth);
        List<Bar> bars = canonicalize(daily.bars());
        BarSeries series = new BarSeries(symbol, bars);
        DatasetContent content = DatasetContent.of(series);

        DatasetVersion version = persistVersion(owner, datasetId, series, content, DatasetSource.ALPHA_VANTAGE,
                daily.sourceDetail(), AdjustmentBasis.RAW, bars);

        return DatasetVersionSummary.of(version);
    }

    @Transactional(readOnly = true)
    public List<DatasetVersionSummary> listVersions(UserId owner, long datasetId) {
        Objects.requireNonNull(owner, "owner must not be null");
        loadOwned(owner, datasetId);
        return versionRepository.findAllOwned(datasetId, owner.value()).stream()
                .map(DatasetVersionSummary::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public DatasetVersionSummary getVersion(UserId owner, long datasetId, int versionNumber) {
        Objects.requireNonNull(owner, "owner must not be null");
        DatasetVersion version = versionRepository.findOwned(datasetId, owner.value(), versionNumber)
                .orElseThrow(() -> new DatasetVersionNotFoundException(datasetId, versionNumber));
        return DatasetVersionSummary.of(version);
    }

    @Transactional(readOnly = true)
    public VerifiedDatasetVersion getVerifiedSeries(UserId owner, long datasetId, int versionNumber) {
        Objects.requireNonNull(owner, "owner must not be null");
        DatasetVersion version = versionRepository.findOwned(datasetId, owner.value(), versionNumber)
                .orElseThrow(() -> new DatasetVersionNotFoundException(datasetId, versionNumber));

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

    private DatasetVersion persistVersion(UserId owner, long datasetId, BarSeries series, DatasetContent content,
                                           DatasetSource source, String sourceDetail, AdjustmentBasis basis,
                                           List<Bar> bars) {
        return writeTransactionTemplate.execute(status -> {
            Dataset dataset = datasetRepository.lockByIdAndOwnerId(datasetId, owner.value())
                    .orElseThrow(() -> new DatasetNotFoundException(datasetId));
            if (!dataset.symbol().equals(series.symbol())) {
                throw new IllegalStateException("dataset symbol changed between read and lock: "
                        + dataset.symbol() + " vs " + series.symbol());
            }
            int nextVersionNumber = dataset.allocateNextVersionNumber();
            datasetRepository.saveAndFlush(dataset);

            DatasetVersion created = new DatasetVersion(dataset.id(), nextVersionNumber, dataset.symbol(),
                    source, sourceDetail, basis, content);
            DatasetVersion saved = saveVersionOrThrowConflict(created, datasetId);

            barRepository.insertAll(saved.id(), bars);
            return saved;
        });
    }

    private static List<Bar> canonicalize(List<Bar> bars) {
        List<Bar> canonical = new ArrayList<>(bars.size());
        for (Bar bar : bars) {
            canonical.add(new Bar(bar.date(),
                    DatasetContent.canonicalPrice(bar.open()),
                    DatasetContent.canonicalPrice(bar.high()),
                    DatasetContent.canonicalPrice(bar.low()),
                    DatasetContent.canonicalPrice(bar.close()),
                    bar.volume()));
        }
        return canonical;
    }

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

    private static boolean isConstraint(DataIntegrityViolationException e, String constraintName) {
        Throwable cause = e.getCause();
        if (cause instanceof ConstraintViolationException cve && cve.getConstraintName() != null) {
            return cve.getConstraintName().equalsIgnoreCase(constraintName);
        }
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(constraintName);
    }
}
