package in.vedchangani.parallax.backend.user;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** D-37: {@link ParallaxUserPrincipal} carries the id and erases its hash on demand. */
class ParallaxUserPrincipalTest {

    @Test
    void carriesIdUsernameAndHashUntilErased() {
        ParallaxUserPrincipal principal = new ParallaxUserPrincipal(new UserId(7L), "someone", "{bcrypt}hash");

        assertEquals(new UserId(7L), principal.id());
        assertEquals("someone", principal.getUsername());
        assertEquals("{bcrypt}hash", principal.getPassword());
    }

    @Test
    void eraseCredentialsClearsThePasswordHashOnly() {
        ParallaxUserPrincipal principal = new ParallaxUserPrincipal(new UserId(7L), "someone", "{bcrypt}hash");

        principal.eraseCredentials();

        assertNull(principal.getPassword());
        assertEquals("someone", principal.getUsername());
        assertEquals(new UserId(7L), principal.id());
    }

    @Test
    void hasNoAuthoritiesInV1() {
        ParallaxUserPrincipal principal = new ParallaxUserPrincipal(new UserId(7L), "someone", "{bcrypt}hash");

        assertEquals(0, principal.getAuthorities().size());
    }
}
