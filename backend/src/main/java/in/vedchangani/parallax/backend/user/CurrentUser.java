package in.vedchangani.parallax.backend.user;

/**
 * The backend-facing seam for "who is making this request" (D-31).
 * {@link SeededCurrentUser} is the only implementation until real
 * authentication exists. A later Spring Security-backed implementation
 * replaces only this bean — {@code StrategyService}'s ownership logic
 * depends solely on this interface, never on how identity was established,
 * so that replacement changes nothing else.
 */
public interface CurrentUser {

    UserId id();
}
