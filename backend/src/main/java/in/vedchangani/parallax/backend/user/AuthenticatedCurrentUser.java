package in.vedchangani.parallax.backend.user;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The production {@link CurrentUser} (D-37), replacing the deleted {@code
 * SeededCurrentUser} — exactly the seam D-31 designed this replacement to
 * be, with every other owner-scoped service/repository/controller
 * unchanged. Resolves the owner id from Spring Security's authenticated
 * principal with no database lookup ({@link ParallaxUserPrincipal} already
 * carries the {@link UserId}).
 *
 * <p>Never falls back to a default user: with no authentication in the
 * security context, or a principal that is not a {@link
 * ParallaxUserPrincipal} (only possible if something other than {@link
 * AppUserDetailsService} were ever wired in as the {@code
 * UserDetailsService}, which nothing in this application does), this
 * throws rather than guessing an identity. In the normal request flow this
 * is unreachable for a protected endpoint — {@code SecurityConfig}
 * rejects an unauthenticated request before any controller runs — so a
 * thrown exception here indicates a wiring defect, not a normal
 * unauthenticated request, and is deliberately left to the generic 500
 * handler rather than given its own client-facing mapping.
 */
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
