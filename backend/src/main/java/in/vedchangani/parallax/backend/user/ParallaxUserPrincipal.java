package in.vedchangani.parallax.backend.user;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

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

    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }
}
