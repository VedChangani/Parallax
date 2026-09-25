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

/**
 * JPA entity for {@code dataset} (D-32): a stable identity owned by exactly
 * one {@code app_user}. Unlike {@code Strategy} (D-31), a dataset's {@code
 * name} and {@code symbol} are fixed at creation — D-32 has no metadata
 * PATCH endpoint, so only {@link #latestVersionNumber} ever mutates. {@code
 * symbol} is {@code updatable=false}: it is also the target of {@code
 * DatasetVersion}'s composite foreign key ({@code (dataset_id, symbol) ->
 * dataset(id, symbol)}), so a version's symbol snapshot can never disagree
 * with its parent's.
 *
 * <p>A dataset may exist with no versions: creating a dataset (JSON) and
 * creating its first version (CSV upload) are separate requests, unlike
 * D-31's {@code Strategy}, which is always created together with version 1.
 *
 * <p>Construction and {@link #allocateNextVersionNumber()} are
 * package-private: {@code DatasetService} is the only write path
 * (CLAUDE.md). {@link #allocateNextVersionNumber()} must only be called
 * while holding this row's pessimistic write lock — enforcing that is
 * {@code DatasetService}'s responsibility, not this entity's.
 */
@Entity
@Table(name = "dataset")
public class Dataset {

    /**
     * The V1 symbol grammar (D-32): 1–32 chars, uppercase ASCII
     * letters/digits plus {@code .}/{@code _}/{@code -}, starting
     * alphanumeric. Lowercase is rejected outright, never uppercased.
     * Applied identically here (Bean Validation on the create request) and
     * as the {@code ck_dataset_symbol_format} database CHECK.
     */
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
        // JPA
    }

    /**
     * Creates a brand-new dataset with no versions yet. {@code
     * latestVersionNumber} starts at 0 — the first {@link
     * #allocateNextVersionNumber()} call allocates version 1.
     */
    Dataset(long ownerId, String name, String symbol) {
        this.ownerId = ownerId;
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.symbol = Objects.requireNonNull(symbol, "symbol must not be null");
        this.latestVersionNumber = 0;
    }

    /**
     * Allocates the next version number. Callers must hold this row's
     * {@code PESSIMISTIC_WRITE} lock — this method performs no locking of
     * its own.
     */
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
