package in.vedchangani.parallax.backend.backtest;

import in.vedchangani.parallax.engine.execution.OrderRejection;
import in.vedchangani.parallax.engine.result.BacktestResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
class BacktestRunWriter {

    private final BacktestRunRepository runRepository;
    private final BacktestEquityPointRepository equityPointRepository;
    private final BacktestFillRepository fillRepository;
    private final BacktestRejectionRepository rejectionRepository;

    BacktestRunWriter(BacktestRunRepository runRepository, BacktestEquityPointRepository equityPointRepository,
                       BacktestFillRepository fillRepository, BacktestRejectionRepository rejectionRepository) {
        this.runRepository = runRepository;
        this.equityPointRepository = equityPointRepository;
        this.fillRepository = fillRepository;
        this.rejectionRepository = rejectionRepository;
    }

    @Transactional
    BacktestRun persist(BacktestRunWriteRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        BacktestResult result = request.result();

        BacktestRun run = new BacktestRun(request.ownerId(), request.strategyId(), request.strategyVersionNumber(),
                request.strategyDefinitionHash(), request.datasetId(), request.datasetVersionNumber(),
                request.datasetContentHash(), request.engineSemanticsVersion(), result, request.metrics(),
                request.benchmark());
        BacktestRun saved = runRepository.saveAndFlush(run);
        long runId = saved.id();

        equityPointRepository.insertAll(runId,
                result.equityCurve().stream().map(BacktestEquityPointRow::of).toList());
        fillRepository.insertAll(runId, result.fills().stream().map(BacktestFillRow::of).toList());
        rejectionRepository.insertAll(runId, toRejectionRows(result.rejections()));

        return saved;
    }

    private static List<BacktestRejectionRow> toRejectionRows(List<OrderRejection> rejections) {
        List<BacktestRejectionRow> rows = new ArrayList<>(rejections.size());
        for (int i = 0; i < rejections.size(); i++) {
            rows.add(BacktestRejectionRow.of(i + 1, rejections.get(i)));
        }
        return rows;
    }
}
