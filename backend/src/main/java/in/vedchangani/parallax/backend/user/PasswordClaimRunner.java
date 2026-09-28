package in.vedchangani.parallax.backend.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Claims an existing, still-passwordless {@code app_user} on startup
 * (D-37) — the one path that lets a developer take over the seeded {@code
 * dev} account (or any account created before authentication existed)
 * without a registration endpoint.
 *
 * <p>Runs only when both {@code parallax.auth.claim-username} and {@code
 * parallax.auth.claim-password} (bound from {@code
 * PARALLAX_CLAIM_USERNAME}/{@code PARALLAX_CLAIM_PASSWORD}) are set;
 * otherwise it does nothing. It never creates a user, and it never
 * overwrites an existing hash — a username that already has one is left
 * untouched (logged at INFO, not an error, since re-running with the
 * variables still set is an expected steady state, not a mistake). An
 * unknown username or a password failing {@link PasswordPolicy} fails
 * startup outright rather than silently skipping, since either is more
 * likely a typo than an intentional no-op. No log message or exception
 * ever includes the password itself.
 */
@Component
public class PasswordClaimRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PasswordClaimRunner.class);

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final String claimUsername;
    private final String claimPassword;

    public PasswordClaimRunner(AppUserRepository appUserRepository,
                                PasswordEncoder passwordEncoder,
                                @Value("${parallax.auth.claim-username:}") String claimUsername,
                                @Value("${parallax.auth.claim-password:}") String claimPassword) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.claimUsername = claimUsername;
        this.claimPassword = claimPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (claimUsername.isBlank() || claimPassword.isBlank()) {
            return;
        }

        AppUser user = appUserRepository.findByUsername(claimUsername)
                .orElseThrow(() -> new IllegalStateException(
                        "PARALLAX_CLAIM_USERNAME '" + claimUsername + "' does not exist"));

        if (user.passwordHash() != null) {
            log.info("account '{}' already has a password set; PARALLAX_CLAIM_PASSWORD was ignored", claimUsername);
            return;
        }

        String violation = PasswordPolicy.violation(claimPassword);
        if (violation != null) {
            throw new IllegalStateException("PARALLAX_CLAIM_PASSWORD rejected: " + violation);
        }

        user.assignPassword(passwordEncoder.encode(claimPassword));
        appUserRepository.saveAndFlush(user);
        log.info("claimed account '{}' with a password", claimUsername);
    }
}
