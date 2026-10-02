package in.vedchangani.parallax.backend.user;

public final class InvalidCurrentPasswordException extends RuntimeException {

    public InvalidCurrentPasswordException() {
        super("invalid username or password");
    }
}
