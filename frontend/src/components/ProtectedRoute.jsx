import { Navigate, useLocation } from 'react-router-dom';
import { HOME_BY_ROLE, useAuth } from '../context/AuthContext';
import { Loader } from './ui';

/**
 * Hides pages from users with the wrong role. This is for convenience only -
 * the backend checks the role again on every API call.
 */
export default function ProtectedRoute({ role, children }) {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) return <Loader label="Checking your session..." />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (role && user.role !== role) return <Navigate to={HOME_BY_ROLE[user.role]} replace />;
  return children;
}
