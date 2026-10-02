package in.vedchangani.parallax.backend.user;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthenticatedCurrentUserTest {

    private final AuthenticatedCurrentUser currentUser = new AuthenticatedCurrentUser();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void throwsWhenThereIsNoAuthentication() {
        SecurityContextHolder.clearContext();

        assertThrows(IllegalStateException.class, currentUser::id);
    }

    @Test
    void throwsWhenTheAuthenticationIsNotMarkedAuthenticated() {
        ParallaxUserPrincipal principal = new ParallaxUserPrincipal(new UserId(1L), "someone", "{bcrypt}hash");
        Authentication authentication =
                UsernamePasswordAuthenticationToken.unauthenticated(principal, "irrelevant");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThrows(IllegalStateException.class, currentUser::id);
    }

    @Test
    void throwsWhenThePrincipalIsNotAParallaxUserPrincipal() {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken("someone", "n/a");
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThrows(IllegalStateException.class, currentUser::id);
    }

    @Test
    void returnsThePrincipalsUserIdWhenAuthenticated() {
        ParallaxUserPrincipal principal = new ParallaxUserPrincipal(new UserId(42L), "someone", "{bcrypt}hash");
        Authentication authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertEquals(new UserId(42L), currentUser.id());
    }
}
