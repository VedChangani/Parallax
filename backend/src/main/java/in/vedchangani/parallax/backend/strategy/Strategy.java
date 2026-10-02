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
    }

    Strategy(long ownerId, String name, String description) {
        this.ownerId = ownerId;
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.description = Objects.requireNonNull(description, "description must not be null");
        this.latestVersionNumber = 1;
    }

    int allocateNextVersionNumber() {
        latestVersionNumber++;
        return latestVersionNumber;
    }

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
