package in.vedchangani.parallax.backend.user;

/**
 * The requested username is already taken (D-38: {@code
 * uq_app_user_username}). Matched against the actual database constraint
 * name, never a racy {@code exists()} pre-check (D-31 precedent). Maps to
 * HTTP 409.
 */
public final class DuplicateUsernameException extends RuntimeException {

    public DuplicateUsernameException(String username) {
        super("username " + username + " is already taken");
    }
}
