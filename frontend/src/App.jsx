import { Navigate, Route, Routes } from 'react-router-dom';
import Layout from './components/Layout';
import ProtectedRoute from './components/ProtectedRoute';
import { Loader } from './components/ui';
import { HOME_BY_ROLE, useAuth } from './context/AuthContext';
import Login from './pages/Login';
import Register from './pages/Register';
import CareerMap from './pages/student/CareerMap';
import StudentProfile from './pages/student/StudentProfile';
import StudentJobs from './pages/student/StudentJobs';
import JobDetail from './pages/student/JobDetail';
import StudentApplications from './pages/student/StudentApplications';
import Opportunities from './pages/student/Opportunities';
import RecruiterDashboard from './pages/recruiter/RecruiterDashboard';
import RecruiterJobs from './pages/recruiter/RecruiterJobs';
import JobForm from './pages/recruiter/JobForm';
import Applicants from './pages/recruiter/Applicants';
import RecruiterProfile from './pages/recruiter/RecruiterProfile';
import AdminDashboard from './pages/admin/AdminDashboard';
import WorkshopPlanner from './pages/admin/WorkshopPlanner';
import CertificateReview from './pages/admin/CertificateReview';
import PlacementProfile from './pages/student/PlacementProfile';
import Assessments from './pages/student/Assessments';
import Certifications from './pages/student/Certifications';
import Leaderboard from './pages/student/Leaderboard';
import Roadmap from './pages/student/Roadmap';

function Home() {
  const { user, loading } = useAuth();
  if (loading) return <Loader />;
  return <Navigate to={user ? HOME_BY_ROLE[user.role] : '/login'} replace />;
}

function Section({ role }) {
  return (
    <ProtectedRoute role={role}>
      <Layout />
    </ProtectedRoute>
  );
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />

      <Route path="/student" element={<Section role="STUDENT" />}>
        <Route path="dashboard" element={<CareerMap />} />
        <Route path="opportunities" element={<Opportunities />} />
        <Route path="jobs" element={<StudentJobs />} />
        <Route path="jobs/:id" element={<JobDetail />} />
        <Route path="applications" element={<StudentApplications />} />
        <Route path="profile" element={<StudentProfile />} />
        <Route path="placement" element={<PlacementProfile />} />
        <Route path="assessments" element={<Assessments />} />
        <Route path="certifications" element={<Certifications />} />
        <Route path="leaderboard" element={<Leaderboard />} />
        <Route path="roadmap" element={<Roadmap />} />
      </Route>

      <Route path="/recruiter" element={<Section role="RECRUITER" />}>
        <Route path="dashboard" element={<RecruiterDashboard />} />
        <Route path="jobs" element={<RecruiterJobs />} />
        <Route path="jobs/new" element={<JobForm />} />
        <Route path="jobs/:id/edit" element={<JobForm />} />
        <Route path="jobs/:id/applicants" element={<Applicants />} />
        <Route path="profile" element={<RecruiterProfile />} />
      </Route>

      <Route path="/admin" element={<Section role="ADMIN" />}>
        <Route path="dashboard" element={<AdminDashboard />} />
        <Route path="workshops" element={<WorkshopPlanner />} />
        <Route path="certificates" element={<CertificateReview />} />
        <Route path="leaderboard" element={<Leaderboard admin />} />
      </Route>

      <Route path="*" element={<Home />} />
    </Routes>
  );
}
