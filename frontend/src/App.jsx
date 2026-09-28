import { BrowserRouter } from 'react-router';
import { AuthProvider } from './auth/AuthProvider.jsx';
import { AppRoutes } from './routes.jsx';

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;
