package in.vedchangani.parallax.backend.backtest;

import in.vedchangani.parallax.engine.metrics.BuyAndHoldBenchmark;
import in.vedchangani.parallax.engine.metrics.PerformanceMetrics;
import in.vedchangani.parallax.engine.portfolio.EquityPoint;
import in.vedchangani.parallax.engine.result.BacktestConfig;
import in.vedchangani.parallax.engine.result.BacktestResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

@Entity
@Table(name = "backtest_run")
@Immutable
public class BacktestRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private long ownerId;

    @Column(name = "strategy_id", nullable = false, updatable = false)
    private long strategyId;

    @Column(name = "strategy_version_number", nullable = false, updatable = false)
    private int strategyVersionNumber;

    @Column(name = "strategy_definition_hash", nullable = false, updatable = false)
    private String strategyDefinitionHash;

    @Column(name = "dataset_id", nullable = false, updatable = false)
    private long datasetId;

    @Column(name = "dataset_version_number", nullable = false, updatable = false)
    private int datasetVersionNumber;

    @Column(name = "dataset_content_hash", nullable = false, updatable = false)
    private String datasetContentHash;

    @Column(name = "engine_semantics_version", nullable = false, updatable = false)
    private int engineSemanticsVersion;

    @Column(name = "initial_capital", nullable = false, updatable = false)
    private BigDecimal initialCapital;

    @Column(name = "commission_per_fill", nullable = false, updatable = false)
    private BigDecimal commissionPerFill;

    @Column(name = "slippage_rate", nullable = false, updatable = false)
    private BigDecimal slippageRate;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false, updatable = false)
    private LocalDate endDate;

    @Column(name = "first_evaluable_date", updatable = false)
    private LocalDate firstEvaluableDate;

    @Column(name = "total_commission", nullable = false, updatable = false)
    private BigDecimal totalCommission;

    @Column(name = "total_slippage_cost", nullable = false, updatable = false)
    private BigDecimal totalSlippageCost;

    @Column(name = "total_return", nullable = false, updatable = false)
    private double totalReturn;

    @Column(name = "cagr", updatable = false)
    private Double cagr;

    @Column(name = "volatility", updatable = false)
    private Double volatility;

    @Column(name = "sharpe_ratio", updatable = false)
    private Double sharpeRatio;

    @Column(name = "max_drawdown", nullable = false, updatable = false)
    private double maxDrawdown;

    @Column(name = "closed_trade_count", nullable = false, updatable = false)
    private int closedTradeCount;

    @Column(name = "win_rate", updatable = false)
    private Double winRate;

    @Column(name = "average_win", updatable = false)
    private Double averageWin;

    @Column(name = "average_loss", updatable = false)
    private Double averageLoss;

    @Column(name = "benchmark_cash", nullable = false, updatable = false)
    private BigDecimal benchmarkCash;

    @Column(name = "benchmark_quantity", nullable = false, updatable = false)
    private long benchmarkQuantity;

    @Column(name = "benchmark_cost_basis", nullable = false, updatable = false)
    private BigDecimal benchmarkCostBasis;

    @Column(name = "benchmark_total_return", nullable = false, updatable = false)
    private double benchmarkTotalReturn;

    @Generated
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected BacktestRun() {
    }

    BacktestRun(long ownerId, long strategyId, int strategyVersionNumber, String strategyDefinitionHash,
                long datasetId, int datasetVersionNumber, String datasetContentHash, int engineSemanticsVersion,
                BacktestResult result, PerformanceMetrics metrics, BuyAndHoldBenchmark benchmark) {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(metrics, "metrics must not be null");
        Objects.requireNonNull(benchmark, "benchmark must not be null");

        this.ownerId = ownerId;
        this.strategyId = strategyId;
        this.strategyVersionNumber = strategyVersionNumber;
        this.strategyDefinitionHash =
                Objects.requireNonNull(strategyDefinitionHash, "strategyDefinitionHash must not be null");
        this.datasetId = datasetId;
        this.datasetVersionNumber = datasetVersionNumber;
        this.datasetContentHash = Objects.requireNonNull(datasetContentHash, "datasetContentHash must not be null");
        this.engineSemanticsVersion = engineSemanticsVersion;

        BacktestConfig config = result.config();
        this.initialCapital = config.initialCapital();
        this.commissionPerFill = config.commissionPerFill();
        this.slippageRate = config.slippageRate();
        this.startDate = config.startDate();
        this.endDate = config.endDate();

        this.firstEvaluableDate = result.firstEvaluableDate().orElse(null);
        this.totalCommission = result.totalCommission();
        this.totalSlippageCost = result.totalSlippageCost();

        this.totalReturn = metrics.totalReturn();
        this.cagr = metrics.cagr().isPresent() ? metrics.cagr().getAsDouble() : null;
        this.volatility = metrics.volatility().isPresent() ? metrics.volatility().getAsDouble() : null;
        this.sharpeRatio = metrics.sharpeRatio().isPresent() ? metrics.sharpeRatio().getAsDouble() : null;
        this.maxDrawdown = metrics.maxDrawdown();
        this.closedTradeCount = metrics.closedTradeCount();
        this.winRate = metrics.winRate().isPresent() ? metrics.winRate().getAsDouble() : null;
        this.averageWin = metrics.averageWin().isPresent() ? metrics.averageWin().getAsDouble() : null;
        this.averageLoss = metrics.averageLoss().isPresent() ? metrics.averageLoss().getAsDouble() : null;

        EquityPoint benchmarkReference = benchmark.equityCurve().get(0);
        this.benchmarkCash = benchmarkReference.cash();
        this.benchmarkQuantity = benchmarkReference.quantity();
        this.benchmarkCostBasis = benchmarkReference.costBasis();
        this.benchmarkTotalReturn = benchmark.totalReturn();
    }

    public Long id() {
        return id;
    }

    public long ownerId() {
        return ownerId;
    }

    public long strategyId() {
        return strategyId;
    }

    public int strategyVersionNumber() {
        return strategyVersionNumber;
    }

    public String strategyDefinitionHash() {
        return strategyDefinitionHash;
    }

    public long datasetId() {
        return datasetId;
    }

    public int datasetVersionNumber() {
        return datasetVersionNumber;
    }

    public String datasetContentHash() {
        return datasetContentHash;
    }

    public int engineSemanticsVersion() {
        return engineSemanticsVersion;
    }

    public BigDecimal initialCapital() {
        return initialCapital;
    }

    public BigDecimal commissionPerFill() {
        return commissionPerFill;
    }

    public BigDecimal slippageRate() {
        return slippageRate;
    }

    public LocalDate startDate() {
        return startDate;
    }

    public LocalDate endDate() {
        return endDate;
    }

    public Optional<LocalDate> firstEvaluableDate() {
        return Optional.ofNullable(firstEvaluableDate);
    }

    public BigDecimal totalCommission() {
        return totalCommission;
    }

    public BigDecimal totalSlippageCost() {
        return totalSlippageCost;
    }

    public double totalReturn() {
        return totalReturn;
    }

    public OptionalDouble cagr() {
        return cagr == null ? OptionalDouble.empty() : OptionalDouble.of(cagr);
    }

    public OptionalDouble volatility() {
        return volatility == null ? OptionalDouble.empty() : OptionalDouble.of(volatility);
    }

    public OptionalDouble sharpeRatio() {
        return sharpeRatio == null ? OptionalDouble.empty() : OptionalDouble.of(sharpeRatio);
    }

    public double maxDrawdown() {
        return maxDrawdown;
    }

    public int closedTradeCount() {
        return closedTradeCount;
    }

    public OptionalDouble winRate() {
        return winRate == null ? OptionalDouble.empty() : OptionalDouble.of(winRate);
    }

    public OptionalDouble averageWin() {
        return averageWin == null ? OptionalDouble.empty() : OptionalDouble.of(averageWin);
    }

    public OptionalDouble averageLoss() {
        return averageLoss == null ? OptionalDouble.empty() : OptionalDouble.of(averageLoss);
    }

    public BigDecimal benchmarkCash() {
        return benchmarkCash;
    }

    public long benchmarkQuantity() {
        return benchmarkQuantity;
    }

    public BigDecimal benchmarkCostBasis() {
        return benchmarkCostBasis;
    }

    public double benchmarkTotalReturn() {
        return benchmarkTotalReturn;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
