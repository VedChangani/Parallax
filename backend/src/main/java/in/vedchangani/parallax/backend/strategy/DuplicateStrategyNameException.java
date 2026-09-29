package in.vedchangani.parallax.backend.strategy;

public final class DuplicateStrategyNameException extends RuntimeException {

    public DuplicateStrategyNameException(String name) {
        super("a strategy named " + name + " already exists for this owner");
    }
}
