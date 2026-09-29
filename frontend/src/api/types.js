/**
 * JSDoc typedefs for the D-34 backend REST API's actual request/response
 * shapes. This file has no runtime behavior - it exists purely so other
 * modules can reference these types in their own JSDoc comments. Every
 * field here mirrors a real backend record (see backend/src/main/java/.../api/*).
 * Do not add a field the backend doesn't return.
 */

// --- errors -----------------------------------------------------------

/**
 * The RFC 9457 shape every backend error response uses (D-31 §11). `type`/
 * `instance` are rarely set by this backend; `field`/`errors` and other
 * extension properties (`line`, `date`, `requestedStartDate`, ...) vary by
 * endpoint.
 * @typedef {object} ProblemDetail
 * @property {string} [type]
 * @property {string} [title]
 * @property {number} [status]
 * @property {string} [detail]
 * @property {string} [instance]
 * @property {string} [field]
 * @property {Array<{field: string, message: string}>} [errors]
 */

// --- strategies (D-31) --------------------------------------------------

/**
 * @typedef {object} StrategyResponse
 * @property {number} id
 * @property {string} name
 * @property {string} description
 * @property {number} latestVersionNumber
 * @property {string} createdAt - ISO-8601 instant
 */

/**
 * @typedef {object} StrategyVersionSummaryResponse
 * @property {number} strategyId
 * @property {number} versionNumber
 * @property {number} schemaVersion
 * @property {string} definitionHash
 * @property {string} createdAt
 */

/**
 * @typedef {object} StrategyVersionResponse
 * @property {number} strategyId
 * @property {number} versionNumber
 * @property {number} schemaVersion
 * @property {string} definitionHash
 * @property {string} createdAt
 * @property {StrategyDefinitionDto} definition
 */

/**
 * @typedef {'SMA' | 'EMA' | 'RSI' | 'ATR' | 'ROC'} IndicatorTypeDto
 */

/**
 * @typedef {'GT' | 'LT'} OperatorDto
 */

/**
 * @typedef {{type: 'indicator', indicator: IndicatorTypeDto, period: number}
 *   | {type: 'close'}
 *   | {type: 'constant', value: string}} OperandDto
 */

/**
 * @typedef {{type: 'compare', left: OperandDto, operator: OperatorDto, right: OperandDto}
 *   | {type: 'all', conditions: ConditionDto[]}
 *   | {type: 'any', conditions: ConditionDto[]}} ConditionDto
 */

/**
 * @typedef {{type: 'cashFraction', fraction: string}} PositionSizingDto
 */

/**
 * @typedef {object} StrategyDefinitionDto
 * @property {ConditionDto} entryCondition
 * @property {ConditionDto} exitCondition
 * @property {PositionSizingDto} positionSizing
 */

// --- datasets (D-32/D-33) ------------------------------------------------

/**
 * @typedef {object} DatasetResponse
 * @property {number} id
 * @property {string} name
 * @property {string} symbol
 * @property {number} latestVersionNumber
 * @property {string} createdAt
 */

/**
 * @typedef {'CSV_UPLOAD' | 'ALPHA_VANTAGE'} DatasetSource
 */

/**
 * @typedef {'RAW' | 'SPLIT_ADJUSTED' | 'SPLIT_AND_DIVIDEND_ADJUSTED'} AdjustmentBasis
 */

/**
 * @typedef {object} DatasetVersionResponse
 * @property {number} datasetId
 * @property {number} versionNumber
 * @property {string} symbol
 * @property {DatasetSource} source
 * @property {string} sourceDetail
 * @property {AdjustmentBasis} adjustmentBasis
 * @property {number} barCount
 * @property {string} firstDate - ISO-8601 date
 * @property {string} lastDate - ISO-8601 date
 * @property {string} contentHash
 * @property {string} createdAt
 */

/**
 * @typedef {object} DatasetBarResponse
 * @property {string} date - ISO-8601 date
 * @property {string} open
 * @property {string} high
 * @property {string} low
 * @property {string} close
 * @property {number} volume
 */

/**
 * @typedef {object} DatasetBarsResponse
 * @property {number} datasetId
 * @property {number} versionNumber
 * @property {string} symbol
 * @property {string} contentHash
 * @property {DatasetBarResponse[]} bars
 */

// --- backtest runs (D-34) ------------------------------------------------

/**
 * The `config` sub-object of a create-run request. Every monetary/rate
 * field is a decimal string under the D-30 grammar (see lib/decimal.js) -
 * never a JSON number.
 * @typedef {object} BacktestConfigRequest
 * @property {string} initialCapital
 * @property {string} commissionPerFill
 * @property {string} slippageRate
 * @property {string} startDate - ISO-8601 date
 * @property {string} endDate - ISO-8601 date
 */

/**
 * @typedef {object} CreateBacktestRunRequest
 * @property {number} strategyId
 * @property {number} strategyVersion
 * @property {number} datasetId
 * @property {number} datasetVersion
 * @property {BacktestConfigRequest} config
 */

/**
 * The cheap `GET /api/backtest-runs` list item - identity/metadata only,
 * plus (Phase 9 Batch 1, I8) the date range and returns read straight off
 * the parent row, so a run's history can read as a useful research log
 * without a per-row detail fetch. `totalReturn`/`benchmarkTotalReturn` are
 * plain JSON numbers, matching {@link PerformanceMetricsResponse#totalReturn}
 * and {@link BenchmarkResponse#totalReturn} exactly - never a decimal
 * string, since these are derived statistics, not ledger values (D-26).
 * @typedef {object} BacktestRunSummaryResponse
 * @property {number} id
 * @property {number} strategyId
 * @property {number} strategyVersion
 * @property {string} definitionHash
 * @property {number} datasetId
 * @property {number} datasetVersion
 * @property {string} contentHash
 * @property {number} engineSemanticsVersion
 * @property {string} startDate - ISO-8601 date
 * @property {string} endDate - ISO-8601 date
 * @property {number} totalReturn
 * @property {number} benchmarkTotalReturn
 * @property {string} createdAt
 */

/**
 * @typedef {object} PerformanceMetricsResponse
 * @property {number} totalReturn
 * @property {number | null} cagr
 * @property {number | null} volatility
 * @property {number | null} sharpeRatio
 * @property {number} maxDrawdown
 * @property {number} closedTradeCount
 * @property {number | null} winRate
 * @property {number | null} averageWin
 * @property {number | null} averageLoss
 * @property {number | null} profitFactor - gross profit / |gross loss| over closed trades; null when no closed trade lost money
 */

/**
 * The persisted buy-and-hold benchmark reference state - the same
 * cash/quantity/costBasis at every date (the benchmark never sells).
 * @typedef {object} BenchmarkResponse
 * @property {string} cash
 * @property {number} quantity
 * @property {string} costBasis
 * @property {number} totalReturn
 */

/**
 * The full, integrity-verified detail view: `GET /api/backtest-runs/{id}`.
 * @typedef {object} BacktestRunResponse
 * @property {number} id
 * @property {number} strategyId
 * @property {number} strategyVersion
 * @property {string} definitionHash
 * @property {number} datasetId
 * @property {number} datasetVersion
 * @property {string} contentHash
 * @property {number} engineSemanticsVersion
 * @property {string} initialCapital
 * @property {string} commissionPerFill
 * @property {string} slippageRate
 * @property {string} startDate
 * @property {string} endDate
 * @property {string | null} firstEvaluableDate
 * @property {string} totalCommission
 * @property {string} totalSlippageCost
 * @property {PerformanceMetricsResponse} metrics
 * @property {BenchmarkResponse} benchmark
 * @property {string} createdAt
 */

/**
 * One row of `GET /api/backtest-runs/{id}/equity-curve`.
 * @typedef {object} BacktestEquityPointResponse
 * @property {string} date
 * @property {string} cash
 * @property {number} quantity
 * @property {string} costBasis
 * @property {string} realizedPnl
 * @property {string} close
 * @property {string} marketValue
 * @property {string} equity
 * @property {string} unrealizedPnl
 * @property {string} benchmarkEquity
 * @property {number} drawdown - fraction >= 0 below the running peak equity (0.25 = 25% below peak; 0 at a new high)
 */

/**
 * @typedef {object} BacktestIndicatorValueResponse
 * @property {IndicatorTypeDto} type
 * @property {number} period
 * @property {string} value
 */

/**
 * @typedef {'ENTER' | 'EXIT'} SignalType
 */

/**
 * @typedef {object} BacktestFillResponse
 * @property {number} orderId
 * @property {string} date
 * @property {number} quantity
 * @property {string} referenceOpen
 * @property {string} fillPrice
 * @property {string} commission
 * @property {SignalType} signalType
 * @property {string} signalDate
 * @property {string} signalClose
 * @property {BacktestIndicatorValueResponse[]} signalIndicators
 */

/**
 * @typedef {'ZERO_QUANTITY' | 'INSUFFICIENT_CASH'} RejectionReason
 */

/**
 * One row of `GET /api/backtest-runs/{id}/rejections`. Fields other than
 * `seq`/`reason`/`signalDate`/`signalClose`/`signalIndicators` are `null`
 * for a `ZERO_QUANTITY` rejection (no order was ever created).
 * @typedef {object} BacktestRejectionResponse
 * @property {number} seq
 * @property {RejectionReason} reason
 * @property {number | null} orderId
 * @property {string | null} executionDate
 * @property {number | null} quantity
 * @property {string | null} requiredCash
 * @property {string | null} availableCash
 * @property {string} signalDate
 * @property {string} signalClose
 * @property {BacktestIndicatorValueResponse[]} signalIndicators
 */

/**
 * @typedef {'OPEN' | 'CLOSED'} TradeStatus
 */

/**
 * One row of `GET /api/backtest-runs/{id}/trades`. `exit`/`realizedPnl` are
 * `null` for an `OPEN` trade; `markDate`/`markClose`/`marketValue`/
 * `unrealizedPnl` are `null` for a `CLOSED` one.
 * @typedef {object} BacktestTradeResponse
 * @property {TradeStatus} status
 * @property {BacktestFillResponse} entry
 * @property {BacktestFillResponse | null} exit
 * @property {number} quantity
 * @property {string | null} realizedPnl
 * @property {string} totalCommission
 * @property {string} totalSlippageCost
 * @property {string | null} markDate
 * @property {string | null} markClose
 * @property {string | null} marketValue
 * @property {string | null} unrealizedPnl
 */

export {};
