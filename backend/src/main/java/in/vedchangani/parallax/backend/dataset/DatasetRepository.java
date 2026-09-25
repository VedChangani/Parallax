package in.vedchangani.parallax.backend.dataset;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Owner-scoped repository access for {@link Dataset} (D-32, mirroring
 * D-31's {@code StrategyRepository}). No {@code findAll}, unrestricted
 * {@code findById}, or delete method is exposed — every read takes an
 * owner id, so cross-owner access is impossible through a normal service
 * call.
 */
public interface DatasetRepository extends Repository<Dataset, Long> {

    <S extends Dataset> S saveAndFlush(S dataset);

    Optional<Dataset> findByIdAndOwnerId(long id, long ownerId);

    List<Dataset> findByOwnerIdOrderByIdAsc(long ownerId);

    /**
     * Owner-scoped {@code SELECT ... FOR UPDATE}. Every write to a {@link
     * Dataset} row — including version-number allocation — must go
     * through this lock (mirroring D-31 §4).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Dataset d where d.id = :id and d.ownerId = :ownerId")
    Optional<Dataset> lockByIdAndOwnerId(@Param("id") long id, @Param("ownerId") long ownerId);
}
