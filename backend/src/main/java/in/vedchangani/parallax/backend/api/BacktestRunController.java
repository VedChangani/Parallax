package in.vedchangani.parallax.backend.api;

import in.vedchangani.parallax.backend.backtest.BacktestConfigMapper;
import in.vedchangani.parallax.backend.backtest.BacktestRunDetail;
import in.vedchangani.parallax.backend.backtest.BacktestRunService;
import in.vedchangani.parallax.backend.backtest.BacktestRunSummary;
import in.vedchangani.parallax.backend.backtest.CreateBacktestRunRequest;
import in.vedchangani.parallax.backend.backtest.DatasetVersionRef;
import in.vedchangani.parallax.backend.backtest.StrategyVersionRef;
import in.vedchangani.parallax.backend.strategy.definition.StrategyDefinitionCodec;
import in.vedchangani.parallax.backend.user.CurrentUser;
import in.vedchangani.parallax.engine.result.BacktestConfig;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/backtest-runs")
public class BacktestRunController {

    private final BacktestRunService backtestRunService;
    private final StrategyDefinitionCodec codec;
    private final BacktestConfigMapper configMapper;
    private final CurrentUser currentUser;
    private final Validator validator;

    public BacktestRunController(BacktestRunService backtestRunService, StrategyDefinitionCodec codec,
                                  BacktestConfigMapper configMapper, CurrentUser currentUser, Validator validator) {
        this.backtestRunService = backtestRunService;
        this.codec = codec;
        this.configMapper = configMapper;
        this.currentUser = currentUser;
        this.validator = validator;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BacktestRunResponse> createRun(@RequestBody String body) {
        CreateBacktestRunRequest request = codec.parseRequest(body, CreateBacktestRunRequest.class);
        validate(request);
        BacktestConfig config = configMapper.toEngine(request.config(), "config");

        BacktestRunSummary created = backtestRunService.createRun(currentUser.id(),
                new StrategyVersionRef(request.strategyId(), request.strategyVersion()),
                new DatasetVersionRef(request.datasetId(), request.datasetVersion()), config);

        BacktestRunDetail detail = backtestRunService.getRun(currentUser.id(), created.id());

        return ResponseEntity.created(URI.create("/api/backtest-runs/" + created.id()))
                .body(BacktestRunResponse.of(detail));
    }

    @GetMapping
    public List<BacktestRunSummaryResponse> listRuns() {
        return backtestRunService.listRuns(currentUser.id()).stream()
                .map(BacktestRunSummaryResponse::of)
                .toList();
    }

    @GetMapping("/{id}")
    public BacktestRunResponse getRun(@PathVariable long id) {
        return BacktestRunResponse.of(backtestRunService.getRun(currentUser.id(), id));
    }

    @GetMapping("/{id}/equity-curve")
    public List<BacktestEquityPointResponse> getEquityCurve(@PathVariable long id) {
        BacktestRunDetail detail = backtestRunService.getRun(currentUser.id(), id);
        return BacktestEquityPointResponse.listOf(detail);
    }

    @GetMapping("/{id}/equity-curve.csv")
    public ResponseEntity<String> exportEquityCurve(@PathVariable long id) {
        BacktestRunDetail detail = backtestRunService.getRun(currentUser.id(), id);
        return csv(BacktestCsv.equityCurve(detail), "backtest-" + id + "-equity-curve.csv");
    }

    @GetMapping("/{id}/trades.csv")
    public ResponseEntity<String> exportTrades(@PathVariable long id) {
        BacktestRunDetail detail = backtestRunService.getRun(currentUser.id(), id);
        return csv(BacktestCsv.trades(detail), "backtest-" + id + "-trades.csv");
    }

    private static ResponseEntity<String> csv(String body, String filename) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }

    @GetMapping("/{id}/trades")
    public List<BacktestTradeResponse> getTrades(@PathVariable long id) {
        BacktestRunDetail detail = backtestRunService.getRun(currentUser.id(), id);
        return detail.trades().stream()
                .map(trade -> BacktestTradeResponse.of(trade, detail))
                .toList();
    }

    @GetMapping("/{id}/rejections")
    public List<BacktestRejectionResponse> getRejections(@PathVariable long id) {
        BacktestRunDetail detail = backtestRunService.getRun(currentUser.id(), id);
        return BacktestRejectionResponse.listOf(detail.rejections());
    }

    private <T> void validate(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
