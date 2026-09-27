package in.vedchangani.parallax.backend.user;

import org.springframework.stereotype.Component;

/**
 * The only {@link CurrentUser} implementation until real authentication
 * exists (D-31). Resolves the deterministic seeded development user
 * ({@code username = 'dev'}, inserted by {@code
 * V2__seed_development_user.sql}) on every call, rather than caching it, so
 * a test that swaps the underlying row still observes the change. Tests
 * override this seam entirely with {@code @MockitoBean CurrentUser} rather
 * than relying on the seeded user for multi-owner scenarios.
 */
@Component
public class SeededCurrentUser implements CurrentUser {

    private static final String SEEDED_USERNAME = "dev";

    private final AppUserRepository appUserRepository;

    public SeededCurrentUser(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public UserId id() {
        AppUser user = appUserRepository.findByUsername(SEEDED_USERNAME)
                .orElseThrow(() -> new IllegalStateException(
                        "seeded development user '" + SEEDED_USERNAME + "' is missing; "
                                + "check that Flyway migrations have run"));
        return new UserId(user.id());
    }
}
