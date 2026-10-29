import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import { AuthProvider } from './context/AuthContext';
import { ProtectedRoute } from './components/ProtectedRoute';

import LandingPage        from './pages/LandingPage';
import LoginPage          from './pages/LoginPage';
import RegisterPage       from './pages/RegisterPage';
import ResetPasswordPage  from './pages/ResetPasswordPage';
import HowItWorksPage     from './pages/HowItWorksPage';
import DonorDashboard     from './pages/DonorDashboard';
import NgoDashboard       from './pages/NgoDashboard';
import NgoProfilePage     from './pages/NgoProfilePage';
import DonationDetailPage from './pages/DonationDetailPage';

function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          {/* Public */}
          <Route path="/"             element={<LandingPage />} />
          <Route path="/login"        element={<LoginPage />} />
          <Route path="/register"     element={<RegisterPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />
          <Route path="/how-it-works" element={<HowItWorksPage />} />

          {/* Donor */}
          <Route path="/donor/dashboard" element={
            <ProtectedRoute role="DONOR"><DonorDashboard /></ProtectedRoute>} />
          <Route path="/donor/donations/:id" element={
            <ProtectedRoute role="DONOR"><DonationDetailPage /></ProtectedRoute>} />

          {/* NGO */}
          <Route path="/ngo/dashboard" element={
            <ProtectedRoute role="NGO"><NgoDashboard /></ProtectedRoute>} />
          <Route path="/ngo/profile" element={
            <ProtectedRoute role="NGO"><NgoProfilePage /></ProtectedRoute>} />
          <Route path="/ngo/donations/:id" element={
            <ProtectedRoute role="NGO"><DonationDetailPage /></ProtectedRoute>} />

          {/* Catch-all */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>

        <Toaster position="top-right" toastOptions={{
          style: {
            background: 'white', color: '#1b1b1b',
            border: '1px solid #ddd8b8', borderRadius: 12,
            fontFamily: 'Inter, sans-serif', fontSize: '0.875rem',
            boxShadow: '0 4px 24px rgba(0,0,0,0.1)',
          },
          success: { iconTheme: { primary: '#2d6a4f', secondary: 'white' } },
          error:   { iconTheme: { primary: '#dc2626', secondary: 'white' } },
        }} />
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;
