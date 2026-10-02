package in.vedchangani.parallax.backend.user;

public final class RegistrationDisabledException extends RuntimeException {

    public RegistrationDisabledException() {
        super("registration is currently disabled");
    }
}
