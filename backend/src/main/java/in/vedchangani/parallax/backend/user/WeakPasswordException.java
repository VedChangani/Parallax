package in.vedchangani.parallax.backend.user;

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
