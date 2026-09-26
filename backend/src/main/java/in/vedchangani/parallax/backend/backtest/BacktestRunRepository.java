package in.vedchangani.parallax.backend.backtest;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Owner-scoped repository access for {@link BacktestRun} (D-34 Batch 1,
 * mirroring D-31/D-32's own repositories). No {@code findAll} or
 * unrestricted {@code findById} is exposed — every read takes an owner id,
 * so cross-owner access is impossible through a normal service call. No
 * update or delete method exists, and no lock method: unlike {@code
 * Strategy}/{@code Dataset}, a run has no version counter to allocate under
 * a pessimistic lock — it is inserted exactly once and never touched
 * again.
 */
public interface BacktestRunRepository extends Repository<BacktestRun, Long> {

    <S extends BacktestRun> S saveAndFlush(S run);

    Optional<BacktestRun> findByIdAndOwnerId(long id, long ownerId);

    List<BacktestRun> findByOwnerIdOrderByIdAsc(long ownerId);
}
