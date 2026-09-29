package in.vedchangani.parallax.backend.strategy;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StrategyRepository extends Repository<Strategy, Long> {

    <S extends Strategy> S saveAndFlush(S strategy);

    Optional<Strategy> findByIdAndOwnerId(long id, long ownerId);

    List<Strategy> findByOwnerIdOrderByIdAsc(long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Strategy s where s.id = :id and s.ownerId = :ownerId")
    Optional<Strategy> lockByIdAndOwnerId(@Param("id") long id, @Param("ownerId") long ownerId);
}
