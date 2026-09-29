package in.vedchangani.parallax.backend.dataset;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DatasetRepository extends Repository<Dataset, Long> {

    <S extends Dataset> S saveAndFlush(S dataset);

    Optional<Dataset> findByIdAndOwnerId(long id, long ownerId);

    List<Dataset> findByOwnerIdOrderByIdAsc(long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Dataset d where d.id = :id and d.ownerId = :ownerId")
    Optional<Dataset> lockByIdAndOwnerId(@Param("id") long id, @Param("ownerId") long ownerId);
}
