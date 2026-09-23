package in.vedchangani.parallax.engine.strategy;

/**
 * A V1 comparison operator. Only strict greater-than and strict
 * less-than are supported; equal operand values make both false. There is
 * deliberately no {@code EQ}, {@code NE}, {@code GTE}, {@code LTE},
 * {@code NOT}, or crossover operator.
 */
public enum Operator {
    GT,
    LT
}
