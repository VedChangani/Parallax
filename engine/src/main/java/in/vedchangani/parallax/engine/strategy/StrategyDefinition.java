package in.vedchangani.parallax.engine.strategy;

import in.vedchangani.parallax.engine.indicator.IndicatorSpec;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public record StrategyDefinition(Condition entryCondition, Condition exitCondition,
                                  PositionSizing positionSizing) {

    private static final Comparator<IndicatorSpec> CANONICAL_ORDER =
            Comparator.comparing(IndicatorSpec::type).thenComparingInt(IndicatorSpec::period);

    public StrategyDefinition {
        Objects.requireNonNull(entryCondition, "entryCondition must not be null");
        Objects.requireNonNull(exitCondition, "exitCondition must not be null");
        Objects.requireNonNull(positionSizing, "positionSizing must not be null");
    }

    public List<IndicatorSpec> requiredIndicatorSpecs() {
        Set<IndicatorSpec> specs = new TreeSet<>(CANONICAL_ORDER);
        collect(entryCondition, specs);
        collect(exitCondition, specs);
        return List.copyOf(specs);
    }

    private static void collect(Condition condition, Set<IndicatorSpec> specs) {
        switch (condition) {
            case Condition.Compare compare -> {
                collect(compare.left(), specs);
                collect(compare.right(), specs);
            }
            case Condition.All all -> {
                for (Condition child : all.conditions()) {
                    collect(child, specs);
                }
            }
            case Condition.Any any -> {
                for (Condition child : any.conditions()) {
                    collect(child, specs);
                }
            }
        }
    }

    private static void collect(Operand operand, Set<IndicatorSpec> specs) {
        switch (operand) {
            case Operand.IndicatorRef ref -> specs.add(ref.spec());
            case Operand.Close close -> { }
            case Operand.Constant constant -> { }
        }
    }
}
