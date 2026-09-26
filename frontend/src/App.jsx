import { BrowserRouter } from 'react-router';
import { ErrorBoundary } from './components/ErrorBoundary.jsx';
import { AppRoutes } from './routes.jsx';

function App() {
  return (
    <ErrorBoundary>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </ErrorBoundary>
  );
}

export default App;
