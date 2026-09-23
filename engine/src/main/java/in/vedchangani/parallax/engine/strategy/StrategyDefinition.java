package in.vedchangani.parallax.engine.strategy;

import in.vedchangani.parallax.engine.indicator.IndicatorSpec;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * What to trade: an immutable pairing of an entry condition, an exit
 * condition, and a position-sizing rule. This describes intent only —
 * entry is a flat-to-long intent, exit is a long-to-flat intent. It holds
 * no runtime state: no current position, pending order, cash, price, or
 * portfolio. Whether the strategy is currently flat or long, whether an
 * order can execute, and cash availability are Backtester/Execution/
 * Portfolio concerns, not this type's.
 *
 * <p>Validation here is structural only (non-null components). This is
 * not a strategy-quality validator: Close-only or Constant-only
 * conditions, conditions with no indicators at all, identical entry and
 * exit conditions, the same {@link IndicatorSpec} referenced by both
 * conditions, and multiple periods of one indicator type are all legal.
 */
public record StrategyDefinition(Condition entryCondition, Condition exitCondition,
                                  PositionSizing positionSizing) {

    private static final Comparator<IndicatorSpec> CANONICAL_ORDER =
            Comparator.comparing(IndicatorSpec::type).thenComparingInt(IndicatorSpec::period);

    public StrategyDefinition {
        Objects.requireNonNull(entryCondition, "entryCondition must not be null");
        Objects.requireNonNull(exitCondition, "exitCondition must not be null");
        Objects.requireNonNull(positionSizing, "positionSizing must not be null");
    }

    /**
     * Every {@link IndicatorSpec} referenced by {@code entryCondition} or
     * {@code exitCondition}, distinct and in canonical order (indicator
     * type declaration order, then period ascending — the same order
     * {@code IndicatorSnapshot} uses). Computed on demand from the
     * condition trees on every call; it is not stored, so it cannot drift
     * from the conditions and does not participate in this record's
     * equality.
     */
    public List<IndicatorSpec> requiredIndicatorSpecs() {
        Set<IndicatorSpec> specs = new TreeSet<>(CANONICAL_ORDER);
        collect(entryCondition, specs);
        collect(exitCondition, specs);
        return List.copyOf(specs);
    }

    // Exhaustive over the sealed Condition hierarchy — deliberately no
    // default branch, so a new Condition type fails to compile here until
    // discovery is explicitly updated for it.
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

    // Exhaustive over the sealed Operand hierarchy — deliberately no
    // default branch, for the same reason as above.
    private static void collect(Operand operand, Set<IndicatorSpec> specs) {
        switch (operand) {
            case Operand.IndicatorRef ref -> specs.add(ref.spec());
            case Operand.Close close -> { }
            case Operand.Constant constant -> { }
        }
    }
}
