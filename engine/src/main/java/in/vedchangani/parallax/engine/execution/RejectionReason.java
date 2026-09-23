package in.vedchangani.parallax.engine.execution;

/**
 * Why a {@link SignalEvent}'s intended order did not result in a
 * {@link Fill}. There are exactly two V1 reasons: no order was ever
 * created ({@link #ZERO_QUANTITY}), or an order was created but could
 * not be afforded at execution ({@link #INSUFFICIENT_CASH}). Other
 * reasons (invalid price, market closed, liquidity, and so on) are
 * outside V1 scope.
 */
public enum RejectionReason {
    ZERO_QUANTITY,
    INSUFFICIENT_CASH
}
