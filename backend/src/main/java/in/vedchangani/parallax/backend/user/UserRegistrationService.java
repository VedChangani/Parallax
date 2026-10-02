package in.vedchangani.parallax.backend.user;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

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

    private static boolean isConstraint(DataIntegrityViolationException e, String constraintName) {
        Throwable cause = e.getCause();
        if (cause instanceof ConstraintViolationException cve && cve.getConstraintName() != null) {
            return cve.getConstraintName().equalsIgnoreCase(constraintName);
        }
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(constraintName);
    }
}
