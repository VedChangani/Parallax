package in.vedchangani.parallax.engine.strategy;

import in.vedchangani.parallax.engine.indicator.IndicatorSnapshot;

import java.util.List;
import java.util.Objects;

/**
 * A boolean expression evaluated against a single {@link IndicatorSnapshot}.
 * This is the entire V1 condition grammar: a comparison between two
 * {@link Operand}s, or a logical AND/OR over child conditions. There is no
 * {@code NOT}, no arithmetic, and no general expression language.
 *
 * <p>Evaluation is a pure function of the condition tree and the snapshot.
 * No implementation may access a {@code BarSeries}, a {@code Bar}, a
 * runtime {@code Indicator}, {@code Portfolio} state, orders, the system
 * clock, or any external service — the snapshot is the only input.
 */
public sealed interface Condition {

    /**
     * Evaluates this condition against {@code snapshot}.
     */
    boolean evaluate(IndicatorSnapshot snapshot);

    /**
     * {@code left <operator> right}, using {@code double} comparison
     * consistent with the engine's numerical policy. A {@code Constant} vs
     * {@code Constant} comparison and identical operands are both allowed:
     * they are valid, deterministic expressions even though their result
     * never depends on the snapshot.
     */
    record Compare(Operand left, Operator operator, Operand right) implements Condition {

        public Compare {
            Objects.requireNonNull(left, "left must not be null");
            Objects.requireNonNull(operator, "operator must not be null");
            Objects.requireNonNull(right, "right must not be null");
        }

        /**
         * Rejects a null snapshot even when neither operand reads it (for
         * example {@code Constant} vs {@code Constant}). {@code Compare} is
         * the only leaf of the sealed hierarchy and every non-empty
         * {@code All}/{@code Any} evaluates at least its first child, so
         * this single check makes every condition tree fail fast on null.
         */
        @Override
        public boolean evaluate(IndicatorSnapshot snapshot) {
            Objects.requireNonNull(snapshot, "snapshot must not be null");
            double leftValue = left.resolve(snapshot);
            double rightValue = right.resolve(snapshot);
            return switch (operator) {
                case GT -> leftValue > rightValue;
                case LT -> leftValue < rightValue;
            };
        }
    }

    /**
     * Logical AND over {@code conditions}, evaluated in list order with
     * short-circuiting: evaluation stops at the first {@code false}. The
     * list must be non-null, non-empty, and contain no null element; an
     * empty {@code All} is rejected rather than treated as vacuously true,
     * since it would otherwise enter on every bar. Nesting (for example an
     * {@code All} containing an {@code Any}) is allowed.
     */
    record All(List<Condition> conditions) implements Condition {

        public All {
            Objects.requireNonNull(conditions, "conditions must not be null");
            conditions = List.copyOf(conditions);
            if (conditions.isEmpty()) {
                throw new IllegalArgumentException("conditions must not be empty");
            }
        }

        @Override
        public boolean evaluate(IndicatorSnapshot snapshot) {
            for (Condition condition : conditions) {
                if (!condition.evaluate(snapshot)) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Logical OR over {@code conditions}, evaluated in list order with
     * short-circuiting: evaluation stops at the first {@code true}. The
     * list must be non-null, non-empty, and contain no null element; an
     * empty {@code Any} is rejected rather than treated as vacuously false,
     * since it would otherwise never trade. Nesting is allowed.
     */
    record Any(List<Condition> conditions) implements Condition {

        public Any {
            Objects.requireNonNull(conditions, "conditions must not be null");
            conditions = List.copyOf(conditions);
            if (conditions.isEmpty()) {
                throw new IllegalArgumentException("conditions must not be empty");
            }
        }

        @Override
        public boolean evaluate(IndicatorSnapshot snapshot) {
            for (Condition condition : conditions) {
                if (condition.evaluate(snapshot)) {
                    return true;
                }
            }
            return false;
        }
    }
}
