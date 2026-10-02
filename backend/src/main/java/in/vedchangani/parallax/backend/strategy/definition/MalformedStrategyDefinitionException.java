package in.vedchangani.parallax.backend.strategy.definition;

public final class MalformedStrategyDefinitionException extends RuntimeException {

    private final String path;

    public MalformedStrategyDefinitionException(String path, String reason) {
        super(path.isEmpty() ? reason : path + ": " + reason);
        this.path = path;
    }

    public String path() {
        return path;
    }
}
