package in.vedchangani.parallax.backend.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class PasswordChangeService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordChangeService(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

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
