package in.vedchangani.parallax.backend.user;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedCurrentUser implements CurrentUser {

    @Override
    public UserId id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("no authenticated user for this request");
        }
        if (!(authentication.getPrincipal() instanceof ParallaxUserPrincipal principal)) {
            throw new IllegalStateException(
                    "authenticated principal is not a ParallaxUserPrincipal: "
                            + authentication.getPrincipal().getClass());
        }
        return principal.id();
    }
}
