package in.vedchangani.parallax.backend.dataset.csv;

public final class InvalidCsvDataException extends RuntimeException {

    private final int line;

    public InvalidCsvDataException(int line, String reason) {
        super("line " + line + ": " + reason);
        this.line = line;
    }

    public int line() {
        return line;
    }
}
