package in.vedchangani.parallax.backend.strategy;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Owner-scoped repository access for {@link StrategyVersion} (D-31). There
 * is no {@code owner_id} column on {@code strategy_version} — ownership is
 * inherited through a join to {@link Strategy}, enforced here rather than by
 * loading a version and checking ownership afterward. No update or delete
 * method exists (D-31 §12: immutability).
 */
public interface StrategyVersionRepository extends Repository<StrategyVersion, Long> {

    <S extends StrategyVersion> S saveAndFlush(S version);

    @Query("""
            select v from StrategyVersion v, Strategy s
            where s.id = v.strategyId and s.id = :strategyId and s.ownerId = :ownerId
              and v.versionNumber = :versionNumber
            """)
    Optional<StrategyVersion> findOwned(@Param("strategyId") long strategyId, @Param("ownerId") long ownerId,
                                         @Param("versionNumber") int versionNumber);

    @Query("""
            select v from StrategyVersion v, Strategy s
            where s.id = v.strategyId and s.id = :strategyId and s.ownerId = :ownerId
            order by v.versionNumber
            """)
    List<StrategyVersion> findAllOwned(@Param("strategyId") long strategyId, @Param("ownerId") long ownerId);
}
