package in.vedchangani.parallax.backend.strategy;

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
 * JPA entity for {@code strategy} (D-31): a stable identity owned by exactly
 * one {@code app_user}, with mutable metadata (name, description) but an
 * immutable version history. No association/collection to {@link
 * StrategyVersion} is modeled — a plain foreign-key column on the version
 * side is enough, and {@code open-in-view=false} makes an association
 * unsafe to leave lazily unfetched outside a transaction anyway (D-31 §4).
 *
 * <p>Construction and every mutator are package-private: {@code
 * StrategyService} is the only write path (CLAUDE.md). There are no public
 * setters. {@link #allocateNextVersionNumber()} must only be called while
 * holding this row's pessimistic write lock (D-31 §4) — enforcing that is
 * {@code StrategyService}'s responsibility, not this entity's.
 */
@Entity
@Table(name = "strategy")
public class Strategy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private long ownerId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "latest_version_number", nullable = false)
    private int latestVersionNumber;

    @Generated
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected Strategy() {
        // JPA
    }

    /**
     * Creates a brand-new strategy at version 1 — the version 1 {@link
     * StrategyVersion} row itself is created separately by the caller in
     * the same transaction (D-31 §4: no lock is required for this case,
     * since the row is invisible to other transactions until commit).
     */
    Strategy(long ownerId, String name, String description) {
        this.ownerId = ownerId;
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.description = Objects.requireNonNull(description, "description must not be null");
        this.latestVersionNumber = 1;
    }

    /**
     * Allocates the next version number. Callers must hold this row's
     * {@code PESSIMISTIC_WRITE} lock (D-31 §4) — this method performs no
     * locking of its own.
     */
    int allocateNextVersionNumber() {
        latestVersionNumber++;
        return latestVersionNumber;
    }

    /** Metadata only. Never touches any {@link StrategyVersion}. */
    void updateMetadata(String name, String description) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.description = Objects.requireNonNull(description, "description must not be null");
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

    public String description() {
        return description;
    }

    public int latestVersionNumber() {
        return latestVersionNumber;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
