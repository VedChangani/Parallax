package in.vedchangani.parallax.backend.dataset;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DatasetVersionRepository extends Repository<DatasetVersion, Long> {

    <S extends DatasetVersion> S saveAndFlush(S version);

    @Query("""
            select v from DatasetVersion v, Dataset d
            where d.id = v.datasetId and d.id = :datasetId and d.ownerId = :ownerId
              and v.versionNumber = :versionNumber
            """)
    Optional<DatasetVersion> findOwned(@Param("datasetId") long datasetId, @Param("ownerId") long ownerId,
                                        @Param("versionNumber") int versionNumber);

    @Query("""
            select v from DatasetVersion v, Dataset d
            where d.id = v.datasetId and d.id = :datasetId and d.ownerId = :ownerId
            order by v.versionNumber
            """)
    List<DatasetVersion> findAllOwned(@Param("datasetId") long datasetId, @Param("ownerId") long ownerId);
}
