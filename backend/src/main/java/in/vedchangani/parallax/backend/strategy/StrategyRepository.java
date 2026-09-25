package in.vedchangani.parallax.backend.strategy;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Owner-scoped repository access for {@link Strategy} (D-31). No {@code
 * findAll}, unrestricted {@code findById}, or delete method is exposed —
 * every read takes an owner id, so cross-owner access is impossible through
 * a normal service call (D-31 §10). Not a generic base repository: only the
 * methods {@code StrategyService} actually needs.
 */
public interface StrategyRepository extends Repository<Strategy, Long> {

    <S extends Strategy> S saveAndFlush(S strategy);

    Optional<Strategy> findByIdAndOwnerId(long id, long ownerId);

    List<Strategy> findByOwnerIdOrderByIdAsc(long ownerId);

    /**
     * Owner-scoped {@code SELECT ... FOR UPDATE}. Every write to a {@link
     * Strategy} row — including a metadata PATCH — must go through this
     * lock (D-31 §4): Hibernate's default UPDATE writes every column, so an
     * unlocked metadata read/write could otherwise race a concurrent {@code
     * createVersion} and silently revert {@code latest_version_number}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Strategy s where s.id = :id and s.ownerId = :ownerId")
    Optional<Strategy> lockByIdAndOwnerId(@Param("id") long id, @Param("ownerId") long ownerId);
}
