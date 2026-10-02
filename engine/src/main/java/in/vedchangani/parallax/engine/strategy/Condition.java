package in.vedchangani.parallax.engine.strategy;

import in.vedchangani.parallax.engine.indicator.IndicatorSnapshot;

import java.util.List;
import java.util.Objects;

public sealed interface Condition {

    boolean evaluate(IndicatorSnapshot snapshot);

    record Compare(Operand left, Operator operator, Operand right) implements Condition {

        public Compare {
            Objects.requireNonNull(left, "left must not be null");
            Objects.requireNonNull(operator, "operator must not be null");
            Objects.requireNonNull(right, "right must not be null");
        }

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
