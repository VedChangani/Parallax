package in.vedchangani.parallax.backend.user;

import org.springframework.data.repository.Repository;

import java.util.Optional;

/**
 * Read-only D-31 repository. No {@code save}, {@code delete}, or generic
 * {@code findById}/{@code findAll} is exposed — nothing in this batch
 * creates or modifies an {@link AppUser}.
 */
public interface AppUserRepository extends Repository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);
}
