package in.vedchangani.parallax.backend.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

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
