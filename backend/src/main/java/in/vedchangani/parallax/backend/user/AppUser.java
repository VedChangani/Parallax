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

/**
 * JPA entity for {@code app_user} (D-31, extended by D-37/D-38). {@code
 * password_hash} is nullable: {@code null} means the account cannot
 * authenticate — the state of every account created before D-37, including
 * the seeded {@code dev} row. {@link AppUserRepository} exposes only
 * {@code findByUsername} and {@code saveAndFlush}; there is still no
 * delete or generic {@code findAll}.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    /**
     * D-38: lowercase-only (so the case-sensitive {@code
     * uq_app_user_username} constraint also behaves as case-insensitive
     * uniqueness), 3–64 characters, matching the {@code username
     * varchar(64)} column exactly at the upper bound.
     */
    public static final String USERNAME_PATTERN = "^[a-z0-9][a-z0-9._-]{2,63}$";

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
        // JPA
    }

    /**
     * Self-registration construction (D-38): unlike the D-37 claim flow,
     * the hash is known and set at creation, never {@code null}. Package-
     * private — {@code UserRegistrationService} is the only caller.
     */
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

    /** The {@code {id}encodedHash} DelegatingPasswordEncoder value, or {@code null} if this account has none (D-37). */
    public String passwordHash() {
        return passwordHash;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /**
     * Sets this account's password hash (D-37). Package-private: {@code
     * PasswordClaimRunner} is the only caller in this batch, and it never
     * calls this when {@link #passwordHash} is already set — enforced here
     * too, defensively, so no future caller can silently overwrite an
     * existing credential.
     */
    void assignPassword(String encodedHash) {
        if (this.passwordHash != null) {
            throw new IllegalStateException("app_user " + id + " already has a password_hash");
        }
        this.passwordHash = encodedHash;
    }

    /**
     * Replaces an already-set password hash (D-40) — the counterpart to
     * {@link #assignPassword}, which only ever fills a {@code null} one.
     * Package-private: {@code PasswordChangeService} is the only caller,
     * and only after it has already verified the caller's current
     * password itself.
     */
    void changePassword(String encodedHash) {
        this.passwordHash = Objects.requireNonNull(encodedHash, "passwordHash must not be null");
    }
}
