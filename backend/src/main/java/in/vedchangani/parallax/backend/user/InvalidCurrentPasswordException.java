package in.vedchangani.parallax.backend.user;

/**
 * The submitted {@code currentPassword} does not match the authenticated
 * caller's stored hash (D-40). Maps to HTTP 401 with the exact same
 * generic body {@code GenericAuthenticationFailureHandler} uses for a
 * failed login — the wrong-current-password case here is the same kind of
 * fact as a wrong login password, so it gets the same response, never a
 * hint about which part of the request was correct.
 */
public final class InvalidCurrentPasswordException extends RuntimeException {

    public InvalidCurrentPasswordException() {
        super("invalid username or password");
    }
}
