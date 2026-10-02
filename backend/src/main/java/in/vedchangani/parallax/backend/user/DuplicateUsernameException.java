package in.vedchangani.parallax.backend.user;

public final class DuplicateUsernameException extends RuntimeException {

    public DuplicateUsernameException(String username) {
        super("username " + username + " is already taken");
    }
}
