import { Navigate, Route, Routes } from 'react-router';
import { AppLayout } from './components/AppLayout.jsx';
import { NotFound } from './components/NotFound.jsx';
import { RequireAuth } from './auth/RequireAuth.jsx';
import { LoginPage } from './pages/auth/LoginPage.jsx';
import { RegisterPage } from './pages/auth/RegisterPage.jsx';
import { BacktestNewPage } from './pages/backtests/BacktestNewPage.jsx';
import { BacktestOverviewPage } from './pages/backtests/BacktestOverviewPage.jsx';
import { BacktestRejectionsPage } from './pages/backtests/BacktestRejectionsPage.jsx';
import { BacktestRunLayout } from './pages/backtests/BacktestRunLayout.jsx';
import { BacktestsListPage } from './pages/backtests/BacktestsListPage.jsx';
import { BacktestTradesPage } from './pages/backtests/BacktestTradesPage.jsx';
import { DatasetDetailPage } from './pages/datasets/DatasetDetailPage.jsx';
import { DatasetsListPage } from './pages/datasets/DatasetsListPage.jsx';
import { DatasetVersionDetailPage } from './pages/datasets/DatasetVersionDetailPage.jsx';
import { StrategyDetailPage } from './pages/strategies/StrategyDetailPage.jsx';
import { StrategyNewPage } from './pages/strategies/StrategyNewPage.jsx';
import { StrategiesListPage } from './pages/strategies/StrategiesListPage.jsx';
import { StrategyVersionDetailPage } from './pages/strategies/StrategyVersionDetailPage.jsx';
import { StrategyVersionNewPage } from './pages/strategies/StrategyVersionNewPage.jsx';

/**
 * The approved Phase 8 Foundation route structure, in declarative mode,
 * now split by D-39 into public routes (`/login`, `/register`, and the
 * catch-all) and everything else, gated behind {@link RequireAuth}. Every
 * route - public and protected - still renders inside the same
 * {@link AppLayout} shell, so the visual system never diverges by auth
 * state (Nav itself adapts).
 */
export function AppRoutes() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />

        <Route element={<RequireAuth />}>
          <Route path="/" element={<Navigate to="/backtests" replace />} />

          <Route path="/strategies" element={<StrategiesListPage />} />
          <Route path="/strategies/new" element={<StrategyNewPage />} />
          <Route path="/strategies/:id" element={<StrategyDetailPage />} />
          <Route path="/strategies/:id/versions/new" element={<StrategyVersionNewPage />} />
          <Route path="/strategies/:id/versions/:version" element={<StrategyVersionDetailPage />} />

          <Route path="/datasets" element={<DatasetsListPage />} />
          <Route path="/datasets/:id" element={<DatasetDetailPage />} />
          <Route path="/datasets/:id/versions/:version" element={<DatasetVersionDetailPage />} />

          <Route path="/backtests" element={<BacktestsListPage />} />
          <Route path="/backtests/new" element={<BacktestNewPage />} />
          <Route path="/backtests/:runId" element={<BacktestRunLayout />}>
            <Route index element={<BacktestOverviewPage />} />
            <Route path="trades" element={<BacktestTradesPage />} />
            <Route path="rejections" element={<BacktestRejectionsPage />} />
          </Route>
        </Route>

        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  );
}
