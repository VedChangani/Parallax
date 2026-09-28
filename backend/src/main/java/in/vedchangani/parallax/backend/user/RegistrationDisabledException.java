package in.vedchangani.parallax.backend.user;

/**
 * Self-registration is switched off ({@code
 * parallax.auth.registration-enabled=false}, D-38). Maps to HTTP 403 —
 * the request is syntactically and semantically fine, it is simply not
 * permitted right now.
 */
public final class RegistrationDisabledException extends RuntimeException {

    public RegistrationDisabledException() {
        super("registration is currently disabled");
    }
}
