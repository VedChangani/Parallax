package in.vedchangani.parallax.backend.user;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * D-38 self-registration: the only write path for creating a new {@link
 * AppUser}. Reuses {@link PasswordPolicy} unchanged from the D-37 claim
 * flow, and mirrors {@code StrategyService.createStrategy}'s exact
 * duplicate-detection idiom — a {@code saveAndFlush} inside a try block,
 * matched against the actual {@code uq_app_user_username} constraint name,
 * never a racy {@code exists()} pre-check, which cannot see a concurrent
 * writer between the check and the insert.
 *
 * <p>Never logs the caller in: {@code AuthController} calls this and
 * returns {@code 201}, and the frontend performs a separate {@code POST
 * /api/auth/login} afterward, so session creation has exactly one code
 * path (D-37's {@code formLogin}).
 */
@Service
public class UserRegistrationService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean registrationEnabled;

    public UserRegistrationService(AppUserRepository appUserRepository,
                                    PasswordEncoder passwordEncoder,
                                    @Value("${parallax.auth.registration-enabled:true}") boolean registrationEnabled) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.registrationEnabled = registrationEnabled;
    }

    /**
     * @throws RegistrationDisabledException if {@code
     *     parallax.auth.registration-enabled} is {@code false} (403)
     * @throws WeakPasswordException if {@code password} fails {@link
     *     PasswordPolicy} (400)
     * @throws DuplicateUsernameException if {@code username} is already
     *     taken (409) — decided by the database constraint, not a
     *     pre-check
     */
    @Transactional
    public void register(String username, String password) {
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(password, "password must not be null");

        if (!registrationEnabled) {
            throw new RegistrationDisabledException();
        }

        String violation = PasswordPolicy.violation(password);
        if (violation != null) {
            throw new WeakPasswordException("password", violation);
        }

        AppUser user = new AppUser(username, passwordEncoder.encode(password));
        try {
            appUserRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            if (isConstraint(e, "uq_app_user_username")) {
                throw new DuplicateUsernameException(username);
            }
            throw e;
        }
    }

    /**
     * Matches a failed write against a specific database constraint name,
     * never SQL state or message text alone where a constraint name is
     * available (mirrors {@code StrategyService}'s own helper exactly).
     */
    private static boolean isConstraint(DataIntegrityViolationException e, String constraintName) {
        Throwable cause = e.getCause();
        if (cause instanceof ConstraintViolationException cve && cve.getConstraintName() != null) {
            return cve.getConstraintName().equalsIgnoreCase(constraintName);
        }
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(constraintName);
    }
}
