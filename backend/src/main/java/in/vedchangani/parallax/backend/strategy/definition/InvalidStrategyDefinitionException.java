package in.vedchangani.parallax.backend.strategy.definition;

public final class InvalidStrategyDefinitionException extends RuntimeException {

    private final String path;

    public InvalidStrategyDefinitionException(String path, String engineMessage) {
        super(path.isEmpty() ? engineMessage : path + ": " + engineMessage);
        this.path = path;
    }

    public String path() {
        return path;
    }
}
