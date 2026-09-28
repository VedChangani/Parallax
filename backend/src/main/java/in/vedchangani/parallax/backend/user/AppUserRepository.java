package in.vedchangani.parallax.backend.user;

import org.springframework.data.repository.Repository;

import java.util.Optional;

/**
 * A deliberately narrow repository (D-31, extended by D-37/D-38/D-40):
 * only {@code findByUsername}, {@code findById}, and {@code saveAndFlush}
 * are exposed — declaring exactly these signatures is enough for Spring
 * Data to implement them, with no need to extend a wider base repository.
 * {@code saveAndFlush} (not plain {@code save}), mirroring {@code
 * StrategyRepository}: a write's constraint violation (D-38's {@code
 * uq_app_user_username}) must surface synchronously as a catchable
 * exception, never deferred to a later, unrelated flush. {@code findById}
 * (D-40) is the one owner-id-based lookup this package needs —
 * {@code PasswordChangeService} loads the authenticated caller's own row
 * by the id {@code CurrentUser} already resolved, never by any id a
 * request could supply. There is still no {@code delete} and no generic
 * {@code findAll}.
 */
public interface AppUserRepository extends Repository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findById(long id);

    <S extends AppUser> S saveAndFlush(S appUser);
}
