package in.vedchangani.parallax.backend.strategy;

import in.vedchangani.parallax.backend.strategy.definition.CanonicalStrategyDefinition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity for {@code strategy_version} (D-31): immutable once created —
 * no update, no delete, backstopped at four layers (D-31 §12): the
 * database's own row/statement triggers, {@link Immutable} plus {@code
 * updatable=false} here, no setters on this entity, and no repository or
 * REST update/delete path. {@code strategyId} is a plain foreign-key
 * column, not an association — there is no back-reference to {@link
 * Strategy}, and ownership is always resolved through a {@code Strategy}
 * join at the repository, never navigated from a loaded version.
 *
 * <p>{@code definitionJson} holds exactly the D-30 {@link
 * CanonicalStrategyDefinition#json()} canonical text passed to the
 * constructor — nothing here re-serializes a {@code StrategyDefinition}.
 * PostgreSQL is free to reformat the stored {@code jsonb} value; D-30's
 * {@code decode} re-encodes and re-hashes rather than ever comparing bytes
 * against this column directly.
 */
@Entity
@Table(name = "strategy_version")
@Immutable
public class StrategyVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "strategy_id", nullable = false, updatable = false)
    private long strategyId;

    @Column(name = "version_number", nullable = false, updatable = false)
    private int versionNumber;

    @Column(name = "definition", columnDefinition = "jsonb", nullable = false, updatable = false)
    @ColumnTransformer(write = "?::jsonb")
    private String definitionJson;

    @Column(name = "definition_schema_version", nullable = false, updatable = false)
    private int definitionSchemaVersion;

    @Column(name = "definition_hash", nullable = false, updatable = false)
    private String definitionHash;

    @Generated
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected StrategyVersion() {
        // JPA
    }

    /**
     * The only way to build a {@link StrategyVersion}: directly from D-30
     * codec output, so the stored text/schema-version/hash triple can never
     * drift from what the codec actually produced.
     */
    StrategyVersion(long strategyId, int versionNumber, CanonicalStrategyDefinition canonical) {
        Objects.requireNonNull(canonical, "canonical must not be null");
        this.strategyId = strategyId;
        this.versionNumber = versionNumber;
        this.definitionJson = canonical.json();
        this.definitionSchemaVersion = canonical.schemaVersion();
        this.definitionHash = canonical.sha256();
    }

    public Long id() {
        return id;
    }

    public long strategyId() {
        return strategyId;
    }

    public int versionNumber() {
        return versionNumber;
    }

    public String definitionJson() {
        return definitionJson;
    }

    public int definitionSchemaVersion() {
        return definitionSchemaVersion;
    }

    public String definitionHash() {
        return definitionHash;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
