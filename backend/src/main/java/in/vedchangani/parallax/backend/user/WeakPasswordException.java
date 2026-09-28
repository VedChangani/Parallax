package in.vedchangani.parallax.backend.user;

/**
 * A submitted password fails {@link PasswordPolicy} (D-38 registration,
 * D-40 password change). {@code field} names the actual request property
 * that failed — {@code "password"} for registration, {@code
 * "newPassword"} for a password change — so {@code ApiExceptionHandler}
 * can report it accurately instead of assuming one call site. The message
 * is always {@link PasswordPolicy#violation(String)}'s own client-safe
 * text — never the password itself. Maps to HTTP 400: this is a shape
 * failure of the request, exactly like a malformed field, not a 422
 * semantic-validity question.
 */
public final class WeakPasswordException extends RuntimeException {

    private final String field;

    public WeakPasswordException(String field, String violation) {
        super(violation);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
