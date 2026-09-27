package in.vedchangani.parallax.backend.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;

import java.time.Instant;

/**
 * JPA entity for {@code app_user} (D-31): the minimum V1 user model. No
 * {@code password_hash} yet — real authentication is a later batch (see
 * docs/decisions.md D-31). Read-only in this batch: nothing outside the
 * {@code V2__seed_development_user.sql} migration creates a row, and
 * {@link AppUserRepository} exposes no write method.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, updatable = false)
    private String username;

    @Generated
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected AppUser() {
        // JPA
    }

    public Long id() {
        return id;
    }

    public String username() {
        return username;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
