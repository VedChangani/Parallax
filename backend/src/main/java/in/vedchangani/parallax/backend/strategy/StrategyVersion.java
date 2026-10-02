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
    }

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
