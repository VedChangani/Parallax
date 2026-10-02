package in.vedchangani.parallax.backend.dataset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "dataset_version")
@Immutable
public class DatasetVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dataset_id", nullable = false, updatable = false)
    private long datasetId;

    @Column(name = "version_number", nullable = false, updatable = false)
    private int versionNumber;

    @Column(name = "symbol", nullable = false, updatable = false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, updatable = false)
    private DatasetSource source;

    @Column(name = "source_detail", nullable = false, updatable = false)
    private String sourceDetail;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_basis", nullable = false, updatable = false)
    private AdjustmentBasis adjustmentBasis;

    @Column(name = "bar_count", nullable = false, updatable = false)
    private int barCount;

    @Column(name = "first_date", nullable = false, updatable = false)
    private LocalDate firstDate;

    @Column(name = "last_date", nullable = false, updatable = false)
    private LocalDate lastDate;

    @Column(name = "content_hash", nullable = false, updatable = false)
    private String contentHash;

    @Generated
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected DatasetVersion() {
    }

    DatasetVersion(long datasetId, int versionNumber, String symbol, DatasetSource source, String sourceDetail,
                   AdjustmentBasis adjustmentBasis, DatasetContent content) {
        Objects.requireNonNull(content, "content must not be null");
        this.datasetId = datasetId;
        this.versionNumber = versionNumber;
        this.symbol = Objects.requireNonNull(symbol, "symbol must not be null");
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.sourceDetail = Objects.requireNonNull(sourceDetail, "sourceDetail must not be null");
        this.adjustmentBasis = Objects.requireNonNull(adjustmentBasis, "adjustmentBasis must not be null");
        this.barCount = content.barCount();
        this.firstDate = content.firstDate();
        this.lastDate = content.lastDate();
        this.contentHash = content.contentHash();
    }

    public Long id() {
        return id;
    }

    public long datasetId() {
        return datasetId;
    }

    public int versionNumber() {
        return versionNumber;
    }

    public String symbol() {
        return symbol;
    }

    public DatasetSource source() {
        return source;
    }

    public String sourceDetail() {
        return sourceDetail;
    }

    public AdjustmentBasis adjustmentBasis() {
        return adjustmentBasis;
    }

    public int barCount() {
        return barCount;
    }

    public LocalDate firstDate() {
        return firstDate;
    }

    public LocalDate lastDate() {
        return lastDate;
    }

    public String contentHash() {
        return contentHash;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
