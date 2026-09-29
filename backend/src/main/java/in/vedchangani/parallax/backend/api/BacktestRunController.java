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

/**
 * The D-34 Batch 3 Backtest Run REST API. This controller only parses and
 * validates the HTTP request and delegates to {@link BacktestRunService}
 * for every domain decision: it never invokes {@code Backtester}, never
 * computes {@code PerformanceMetrics}/{@code BuyAndHoldBenchmark}, never
 * derives {@code Trade}s independently of {@link BacktestRunDetail#trades()}
 * (itself a thin {@code Trade.fromFills} delegate), never touches a
 * repository or entity directly, and performs no ownership check of its
 * own — {@code BacktestRunService} is owner-scoped and authoritative for
 * all of that, exactly like {@code StrategyController}/{@code
 * DatasetController}.
 *
 * <p>The request body is read exactly once, by the same strict D-30 {@code
 * JsonMapper} every other envelope uses ({@code
 * StrategyDefinitionCodec#parseRequest(String, Class)}) — never Spring's
 * lenient global JSON binding — so unknown properties (including an
 * attempted client-supplied {@code ownerId}, hash, or {@code
 * engineSemanticsVersion}), missing/null fields, and a JSON number where a
 * decimal string is required all fail before Bean Validation ever runs.
 *
 * <p>Every read endpoint ({@code GET .../{id}}, {@code .../equity-curve},
 * {@code .../trades}, {@code .../rejections}) calls the exact same {@link
 * BacktestRunService#getRun} — the only place full structural and
 * cross-field integrity verification happens — and only then maps
 * different fields of the returned, already-verified {@link
 * BacktestRunDetail} to each endpoint's own response shape. {@link
 * BacktestRunService#listRuns} is deliberately cheap: it never loads or
 * verifies a child row (see {@code BacktestRunService}'s own Javadoc).
 */
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

        // The just-persisted run is read back through the exact same owner-scoped,
        // fully-verifying path any later GET uses - never trusted as-written.
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

    /**
     * The run's equity curve as CSV (D-42), from the same verified {@link
     * BacktestRunDetail} as the JSON view. The content type is set on the
     * response rather than via {@code produces}, so an error (404/500) is
     * still a normal ProblemDetail instead of a content-negotiation failure.
     */
    @GetMapping("/{id}/equity-curve.csv")
    public ResponseEntity<String> exportEquityCurve(@PathVariable long id) {
        BacktestRunDetail detail = backtestRunService.getRun(currentUser.id(), id);
        return csv(BacktestCsv.equityCurve(detail), "backtest-" + id + "-equity-curve.csv");
    }

    /** The run's trades as CSV, one row per trade (D-42). */
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

    /**
     * Explicit Bean Validation of an envelope record already produced by
     * the strict D-30 reader (mirroring {@code StrategyController}/{@code
     * DatasetController}). {@code @Valid} cannot be applied to a raw
     * {@code String} request-body parameter, so this substitutes for it.
     */
    private <T> void validate(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
