package in.vedchangani.parallax.backend.strategy;

import in.vedchangani.parallax.engine.strategy.Condition;
import in.vedchangani.parallax.engine.strategy.Operand;
import in.vedchangani.parallax.engine.strategy.Operator;
import in.vedchangani.parallax.engine.strategy.PositionSizing;
import in.vedchangani.parallax.engine.strategy.StrategyDefinition;

import java.math.BigDecimal;

/** A tiny, deterministic, hand-calculable {@link StrategyDefinition} fixture shared by D-31 tests. */
final class StrategyFixtures {

    private StrategyFixtures() {
    }

    static StrategyDefinition simple() {
        return new StrategyDefinition(
                new Condition.Compare(new Operand.Close(), Operator.GT, new Operand.Constant(0)),
                new Condition.Compare(new Operand.Close(), Operator.LT, new Operand.Constant(0)),
                new PositionSizing.CashFraction(BigDecimal.ONE));
    }

    /** A definition distinct from {@link #simple()} — a different constant threshold. */
    static StrategyDefinition alternative() {
        return new StrategyDefinition(
                new Condition.Compare(new Operand.Close(), Operator.GT, new Operand.Constant(5)),
                new Condition.Compare(new Operand.Close(), Operator.LT, new Operand.Constant(0)),
                new PositionSizing.CashFraction(BigDecimal.ONE));
    }
}
