package in.vedchangani.parallax.backend.user;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * The Spring Security principal for an authenticated {@code app_user}
 * (D-37). Carries the {@link UserId} directly so {@link
 * AuthenticatedCurrentUser} never needs a second database lookup once
 * authentication has already succeeded. There are no authorities in V1 —
 * every authenticated user has the same, single capability set.
 *
 * <p>Implements {@link CredentialsContainer} so {@code
 * DaoAuthenticationProvider} erases {@link #passwordHash} from the
 * principal that is actually stored in the session immediately after
 * authentication succeeds: the hash is needed only to verify the
 * submitted password once, never afterward.
 */
public final class ParallaxUserPrincipal implements UserDetails, CredentialsContainer {

    private final UserId id;
    private final String username;
    private String passwordHash;

    public ParallaxUserPrincipal(UserId id, String username, String passwordHash) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public UserId id() {
        return id;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    /** Erases the hash after authentication succeeds — never retained in the session (D-37). */
    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }
}
