package in.vedchangani.parallax.backend.user;

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
@Table(name = "app_user")
public class AppUser {

    public static final int USERNAME_MAX_LENGTH = 64;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, updatable = false)
    private String username;

    @Column(name = "password_hash")
    private String passwordHash;

    @Generated
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected AppUser() {
    }

    AppUser(String username, String passwordHash) {
        this.username = Objects.requireNonNull(username, "username must not be null");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
    }

    public Long id() {
        return id;
    }

    public String username() {
        return username;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public Instant createdAt() {
        return createdAt;
    }

    void assignPassword(String encodedHash) {
        if (this.passwordHash != null) {
            throw new IllegalStateException("app_user " + id + " already has a password_hash");
        }
        this.passwordHash = encodedHash;
    }

    void changePassword(String encodedHash) {
        this.passwordHash = Objects.requireNonNull(encodedHash, "passwordHash must not be null");
    }
}
