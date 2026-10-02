package in.vedchangani.parallax.backend.backtest;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

public interface BacktestRunRepository extends Repository<BacktestRun, Long> {

    <S extends BacktestRun> S saveAndFlush(S run);

    Optional<BacktestRun> findByIdAndOwnerId(long id, long ownerId);

    List<BacktestRun> findByOwnerIdOrderByIdAsc(long ownerId);
}
