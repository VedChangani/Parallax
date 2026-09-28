package in.vedchangani.parallax.backend.user;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security's {@link UserDetailsService}, backed by {@link
 * AppUserRepository} (D-37). An unknown username and a username with no
 * {@code password_hash} (never claimed — e.g. the seeded {@code dev} user)
 * both throw {@link UsernameNotFoundException}: {@code
 * DaoAuthenticationProvider} maps either one (and a wrong password) to the
 * same {@code BadCredentialsException}, so a login response can never
 * reveal which of the three actually happened.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public AppUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("no such user"));
        String hash = user.passwordHash();
        if (hash == null) {
            throw new UsernameNotFoundException("user has no password set");
        }
        return new ParallaxUserPrincipal(new UserId(user.id()), user.username(), hash);
    }
}
