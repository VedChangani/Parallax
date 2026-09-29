package in.vedchangani.parallax.backend.dataset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "dataset")
public class Dataset {

    public static final String SYMBOL_PATTERN = "^[A-Z0-9][A-Z0-9._-]{0,31}$";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private long ownerId;

    @Column(name = "name", nullable = false, updatable = false)
    private String name;

    @Column(name = "symbol", nullable = false, updatable = false)
    private String symbol;

    @Column(name = "latest_version_number", nullable = false)
    private int latestVersionNumber;

    @Generated
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected Dataset() {
    }

    Dataset(long ownerId, String name, String symbol) {
        this.ownerId = ownerId;
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.symbol = Objects.requireNonNull(symbol, "symbol must not be null");
        this.latestVersionNumber = 0;
    }

    int allocateNextVersionNumber() {
        latestVersionNumber++;
        return latestVersionNumber;
    }

    public Long id() {
        return id;
    }

    public long ownerId() {
        return ownerId;
    }

    public String name() {
        return name;
    }

    public String symbol() {
        return symbol;
    }

    public int latestVersionNumber() {
        return latestVersionNumber;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
