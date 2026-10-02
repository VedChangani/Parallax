package in.vedchangani.parallax.backend.dataset.csv;

public final class MalformedCsvException extends RuntimeException {

    private final int line;

    public MalformedCsvException(int line, String reason) {
        super("line " + line + ": " + reason);
        this.line = line;
    }

    public int line() {
        return line;
    }
}
