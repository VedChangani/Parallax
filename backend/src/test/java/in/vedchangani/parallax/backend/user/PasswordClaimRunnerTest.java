package in.vedchangani.parallax.backend.user;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PasswordClaimRunner} (D-37), against a mocked
 * {@link AppUserRepository} and the real {@code
 * DelegatingPasswordEncoder} — the last case also doubles as the "stored
 * hash is delegated/bcrypt, not plaintext" proof for this batch.
 */
class PasswordClaimRunnerTest {

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Test
    void doesNothingWhenEitherPropertyIsBlank() {
        AppUserRepository repository = mock(AppUserRepository.class);

        new PasswordClaimRunner(repository, passwordEncoder, "", "a-perfectly-fine-password").run(null);
        new PasswordClaimRunner(repository, passwordEncoder, "dev", "").run(null);

        verifyNoInteractions(repository);
    }

    @Test
    void failsStartupWhenTheUsernameDoesNotExist() {
        AppUserRepository repository = mock(AppUserRepository.class);
        when(repository.findByUsername("dev")).thenReturn(Optional.empty());
        PasswordClaimRunner runner =
                new PasswordClaimRunner(repository, passwordEncoder, "dev", "a-perfectly-fine-password");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> runner.run(null));

        assertFalse(exception.getMessage().contains("a-perfectly-fine-password"));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void doesNothingAndNeverOverwritesAnExistingHash() {
        AppUserRepository repository = mock(AppUserRepository.class);
        AppUser user = newUser(1L, "dev", "{bcrypt}already-set");
        when(repository.findByUsername("dev")).thenReturn(Optional.of(user));
        PasswordClaimRunner runner =
                new PasswordClaimRunner(repository, passwordEncoder, "dev", "a-perfectly-fine-password");

        runner.run(null);

        verify(repository, never()).saveAndFlush(any());
        assertEquals("{bcrypt}already-set", user.passwordHash());
    }

    @Test
    void failsStartupOnAWeakPasswordWithoutRevealingItAndWithoutClaiming() {
        AppUserRepository repository = mock(AppUserRepository.class);
        AppUser user = newUser(1L, "dev", null);
        when(repository.findByUsername("dev")).thenReturn(Optional.of(user));
        PasswordClaimRunner runner = new PasswordClaimRunner(repository, passwordEncoder, "dev", "too-short");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> runner.run(null));

        assertFalse(exception.getMessage().contains("too-short"));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void claimsAPasswordlessUserAndStoresADelegatingBcryptHashNeverThePlaintext() {
        AppUserRepository repository = mock(AppUserRepository.class);
        AppUser user = newUser(1L, "dev", null);
        when(repository.findByUsername("dev")).thenReturn(Optional.of(user));
        when(repository.saveAndFlush(user)).thenReturn(user);
        String password = "a-perfectly-fine-password";
        PasswordClaimRunner runner = new PasswordClaimRunner(repository, passwordEncoder, "dev", password);

        runner.run(null);

        verify(repository).saveAndFlush(user);
        String hash = user.passwordHash();
        assertNotNull(hash);
        assertTrue(hash.startsWith("{bcrypt}"));
        assertNotEquals(password, hash);
        assertTrue(passwordEncoder.matches(password, hash));
    }

    private static AppUser newUser(long id, String username, String passwordHash) {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "username", username);
        ReflectionTestUtils.setField(user, "passwordHash", passwordHash);
        return user;
    }
}
