package in.vedchangani.parallax.backend.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * D-40 password change: the only write path that replaces an existing
 * password hash (as opposed to D-37's {@code PasswordClaimRunner}, which
 * only ever fills a {@code null} one, or D-38's {@code
 * UserRegistrationService}, which only ever creates a brand-new row).
 * Reuses {@link PasswordPolicy} unchanged for {@code newPassword} and the
 * same {@link PasswordEncoder} every other authentication path uses — no
 * separate password model or storage.
 *
 * <p>The account to change is always the one {@code userId} names, which
 * {@code AuthController} takes only from {@code CurrentUser} — never from
 * the request body — so this can never change another user's password.
 */
@Service
public class PasswordChangeService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordChangeService(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * @throws InvalidCurrentPasswordException if {@code currentPassword}
     *     does not match the stored hash, or the account has none (401)
     * @throws WeakPasswordException if {@code newPassword} fails {@link
     *     PasswordPolicy} (400)
     */
    @Transactional
    public void changePassword(UserId userId, String currentPassword, String newPassword) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(currentPassword, "currentPassword must not be null");
        Objects.requireNonNull(newPassword, "newPassword must not be null");

        AppUser user = appUserRepository.findById(userId.value())
                .orElseThrow(() -> new IllegalStateException(
                        "authenticated user " + userId.value() + " does not exist"));

        String hash = user.passwordHash();
        if (hash == null || !passwordEncoder.matches(currentPassword, hash)) {
            throw new InvalidCurrentPasswordException();
        }

        String violation = PasswordPolicy.violation(newPassword);
        if (violation != null) {
            throw new WeakPasswordException("newPassword", violation);
        }

        user.changePassword(passwordEncoder.encode(newPassword));
        appUserRepository.saveAndFlush(user);
    }
}
