import { useCallback, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { createBacktestRun } from '../../api/backtests.js';
import { getDatasetVersion, listDatasetVersions, listDatasets } from '../../api/datasets.js';
import { getStrategyVersion, listStrategies, listStrategyVersions } from '../../api/strategies.js';
import { Button } from '../../components/Button.jsx';
import { PageHeader } from '../../components/PageHeader.jsx';
import { validateBacktestConfigForm } from '../../features/backtests/backtestConfigValidation.js';
import { BacktestConfigFields } from '../../features/backtests/BacktestConfigFields.jsx';
import { MarketSnapshotFields } from '../../features/backtests/MarketSnapshotFields.jsx';
import { RunIdentitySummary } from '../../features/backtests/RunIdentitySummary.jsx';
import { StrategySelectionFields } from '../../features/backtests/StrategySelectionFields.jsx';
import { requiredLookbackBars } from '../../features/strategies/definitionMapping.js';
import { useApiResource } from '../../hooks/useApiResource.js';

export function BacktestNewPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const preselectedMarketId = location.state?.marketId;
  const preselectedStrategyId = location.state?.strategyId;

  const markets = useApiResource(listDatasets, []);
  const [explicitMarketId, setExplicitMarketId] = useState(undefined);
  const marketId =
    explicitMarketId ??
    markets.data?.find((market) => market.id === preselectedMarketId)?.id ??
    (markets.data && markets.data.length > 0 ? markets.data[0].id : undefined);

  const fetchVersions = useCallback(
    (signal) => (marketId !== undefined ? listDatasetVersions(marketId, signal) : Promise.resolve([])),
    [marketId],
  );
  const versions = useApiResource(fetchVersions, [marketId]);

  const [explicitSnapshot, setExplicitSnapshot] = useState(undefined);
  const latestSnapshotVersion =
    versions.data && versions.data.length > 0 ? Math.max(...versions.data.map((version) => version.versionNumber)) : undefined;
  const snapshotVersion = explicitSnapshot && explicitSnapshot.marketId === marketId ? explicitSnapshot.version : latestSnapshotVersion;

  function handleMarketChange(id) {
    setExplicitMarketId(id);
  }

  function handleSnapshotChange(version) {
    setExplicitSnapshot({ marketId, version });
  }

  const fetchSnapshotDetail = useCallback(
    (signal) => (marketId !== undefined && snapshotVersion !== undefined ? getDatasetVersion(marketId, snapshotVersion, signal) : Promise.resolve(undefined)),
    [marketId, snapshotVersion],
  );
  const snapshotDetail = useApiResource(fetchSnapshotDetail, [marketId, snapshotVersion], {
    cacheKey: marketId !== undefined && snapshotVersion !== undefined ? `/api/datasets/${marketId}/versions/${snapshotVersion}` : undefined,
  });

  const strategies = useApiResource(listStrategies, []);
  const [explicitStrategyId, setExplicitStrategyId] = useState(undefined);
  const strategyId =
    explicitStrategyId ??
    strategies.data?.find((strategy) => strategy.id === preselectedStrategyId)?.id ??
    (strategies.data && strategies.data.length > 0 ? strategies.data[0].id : undefined);

  const fetchStrategyVersions = useCallback(
    (signal) => (strategyId !== undefined ? listStrategyVersions(strategyId, signal) : Promise.resolve([])),
    [strategyId],
  );
  const strategyVersions = useApiResource(fetchStrategyVersions, [strategyId]);

  const [explicitStrategyVersion, setExplicitStrategyVersion] = useState(undefined);
  const latestStrategyVersion =
    strategyVersions.data && strategyVersions.data.length > 0
      ? Math.max(...strategyVersions.data.map((version) => version.versionNumber))
      : undefined;
  const strategyVersion =
    explicitStrategyVersion && explicitStrategyVersion.strategyId === strategyId
      ? explicitStrategyVersion.version
      : latestStrategyVersion;

  function handleStrategyChange(id) {
    setExplicitStrategyId(id);
  }

  function handleStrategyVersionChange(version) {
    setExplicitStrategyVersion({ strategyId, version });
  }

  const fetchStrategyVersionDetail = useCallback(
    (signal) => (strategyId !== undefined && strategyVersion !== undefined ? getStrategyVersion(strategyId, strategyVersion, signal) : Promise.resolve(undefined)),
    [strategyId, strategyVersion],
  );
  const strategyVersionDetail = useApiResource(fetchStrategyVersionDetail, [strategyId, strategyVersion], {
    cacheKey: strategyId !== undefined && strategyVersion !== undefined ? `/api/strategies/${strategyId}/versions/${strategyVersion}` : undefined,
  });

  const [initialCapital, setInitialCapital] = useState('');
  const [commissionPerFill, setCommissionPerFill] = useState('');
  const [slippagePercent, setSlippagePercent] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [datesTouched, setDatesTouched] = useState(false);
  const [datesPrefilledFrom, setDatesPrefilledFrom] = useState(undefined);

  if (!datesTouched && snapshotDetail.data && datesPrefilledFrom !== snapshotDetail.data) {
    setDatesPrefilledFrom(snapshotDetail.data);
    setStartDate(snapshotDetail.data.firstDate);
    setEndDate(snapshotDetail.data.lastDate);
  }

  function handleStartDateChange(value) {
    setDatesTouched(true);
    setStartDate(value);
  }

  function handleEndDateChange(value) {
    setDatesTouched(true);
    setEndDate(value);
  }

  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState('');
  const [rangeError, setRangeError] = useState(undefined);
  const [submitting, setSubmitting] = useState(false);
  const submittingRef = useRef(false);

  const selectedMarket = markets.data?.find((market) => market.id === marketId);
  const selectedStrategy = strategies.data?.find((strategy) => strategy.id === strategyId);

  const lookbackBars = strategyVersionDetail.data ? requiredLookbackBars(strategyVersionDetail.data.definition) : 0;

  const canSubmit =
    marketId !== undefined &&
    snapshotVersion !== undefined &&
    strategyId !== undefined &&
    strategyVersion !== undefined &&
    !versions.loading &&
    !strategyVersions.loading &&
    !submitting;

  async function handleSubmit(event) {
    event.preventDefault();
    if (submittingRef.current) return;

    setFormError('');
    setRangeError(undefined);

    if (marketId === undefined || snapshotVersion === undefined) {
      setFormError('Choose a market and a data snapshot before running a backtest.');
      return;
    }
    if (strategyId === undefined || strategyVersion === undefined) {
      setFormError('Choose a strategy and a version before running a backtest.');
      return;
    }

    const { fieldErrors: newFieldErrors, config } = validateBacktestConfigForm({
      initialCapital,
      commissionPerFill,
      slippagePercent,
      startDate,
      endDate,
    });
    setFieldErrors(newFieldErrors);
    if (!config) return;

    submittingRef.current = true;
    setSubmitting(true);
    try {
      const created = await createBacktestRun({
        strategyId,
        strategyVersion,
        datasetId: marketId,
        datasetVersion: snapshotVersion,
        config,
      });
      navigate(`/backtests/${created.id}`);
    } catch (error) {
      if (error.kind === 'network') {
        setFormError('Could not reach the backend. Check that it is running and try again.');
      } else if (error.props?.datasetFirstDate !== undefined) {
        setRangeError({
          message: 'Selected dates are outside the available historical data.',
          available: `${error.props.datasetFirstDate} → ${error.props.datasetLastDate}`,
        });
      } else {
        setFormError(error.detail ?? error.title ?? 'Could not create this backtest run.');
      }
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  }

  return (
    <div>
      <PageHeader
        title="New backtest"
        description="Choose a market and strategy, set the execution assumptions, then run a deterministic historical simulation."
      />

      <form onSubmit={handleSubmit} noValidate>
        <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
          <div className="space-y-6">
            <Section title="Market">
              <MarketSnapshotFields
                markets={markets}
                marketId={marketId}
                onMarketChange={handleMarketChange}
                versions={versions}
                snapshotVersion={snapshotVersion}
                onSnapshotChange={handleSnapshotChange}
                snapshotDetail={snapshotDetail}
              />
            </Section>

            <Section title="Strategy">
              <StrategySelectionFields
                strategies={strategies}
                strategyId={strategyId}
                onStrategyChange={handleStrategyChange}
                versions={strategyVersions}
                strategyVersion={strategyVersion}
                onVersionChange={handleStrategyVersionChange}
                versionDetail={strategyVersionDetail}
              />
            </Section>

            <Section title="Configuration">
              <BacktestConfigFields
                initialCapital={initialCapital}
                onInitialCapitalChange={setInitialCapital}
                commissionPerFill={commissionPerFill}
                onCommissionPerFillChange={setCommissionPerFill}
                slippagePercent={slippagePercent}
                onSlippagePercentChange={setSlippagePercent}
                startDate={startDate}
                onStartDateChange={handleStartDateChange}
                endDate={endDate}
                onEndDateChange={handleEndDateChange}
                fieldErrors={fieldErrors}
                coverage={snapshotDetail.data ? { firstDate: snapshotDetail.data.firstDate, lastDate: snapshotDetail.data.lastDate } : undefined}
                requiredLookbackBars={lookbackBars}
              />

              {rangeError ? (
                <div role="alert" className="mt-4 rounded-md border border-danger bg-danger-bg p-3 text-sm text-danger">
                  <p className="font-semibold">{rangeError.message}</p>
                  <p className="mt-1 text-danger/80">Available data: {rangeError.available}</p>
                </div>
              ) : null}
            </Section>
          </div>

          <div className="space-y-4 lg:sticky lg:top-6 lg:self-start">
            <RunIdentitySummary
              market={selectedMarket}
              snapshotVersion={snapshotVersion}
              snapshotDetail={snapshotDetail.data}
              strategy={selectedStrategy}
              strategyVersion={strategyVersion}
              strategyVersionDetail={strategyVersionDetail.data}
            />
          </div>
        </div>

        {formError ? (
          <p role="alert" className="mt-6 text-sm text-danger">
            {formError}
          </p>
        ) : null}

        <div className="mt-6 flex justify-end border-t border-border pt-6">
          <Button
            type="submit"
            variant="primary"
            disabled={!canSubmit}
            aria-busy={submitting}
            className="px-6 py-3 text-base uppercase tracking-wide"
          >
            {submitting ? 'Running backtest…' : 'Run backtest'}
          </Button>
        </div>
      </form>
    </div>
  );
}

function Section({ title, children }) {
  return (
    <section className="rounded-md border border-border bg-surface p-5">
      <h2 className="text-sm font-semibold uppercase tracking-wide text-ink-muted">{title}</h2>
      <div className="mt-4">{children}</div>
    </section>
  );
}
