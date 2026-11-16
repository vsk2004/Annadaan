import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Leaf } from 'lucide-react';
import toast from 'react-hot-toast';
import Navbar from '../components/Navbar';
import { authAPI } from '../api';
import { validatePassword } from '../utils/validation';
import PasswordRequirements from '../components/PasswordRequirements';

export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = searchParams.get('token');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [sent, setSent] = useState(false);

  const handleForgotPassword = async (event) => {
    event.preventDefault();
    setLoading(true);
    try {
      const { data } = await authAPI.forgotPassword({ email: email.trim() });
      setSent(true);
      toast(data.message);
    } catch (error) {
      toast.error(error.response?.data?.message || 'Could not request a password reset. Try again.');
    } finally {
      setLoading(false);
    }
  };

  const handleResetPassword = async (event) => {
    event.preventDefault();
    const passwordError = validatePassword(password);
    if (passwordError) {
      toast.error(passwordError);
      return;
    }
    if (password !== confirmPassword) {
      toast.error('Passwords do not match.');
      return;
    }

    setLoading(true);
    try {
      const { data } = await authAPI.resetPassword({ token, password, confirmPassword });
      toast.success(data.message);
      navigate('/login', { replace: true });
    } catch (error) {
      toast.error(error.response?.data?.message || error.response?.data?.error || 'This reset link is invalid or expired.');
    } finally {
      setLoading(false);
    }
  };

  const isReset = Boolean(token);

  return (
    <div>
      <Navbar />
      <div style={{ minHeight: '85vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'linear-gradient(165deg, var(--brand-cream) 0%, #d8f3dc 100%)', padding: 24 }}>
        <div style={{ background: 'white', borderRadius: 20, padding: '40px 36px', width: '100%', maxWidth: 440, boxShadow: '0 8px 40px rgba(0,0,0,0.1)', border: '1px solid var(--brand-border)' }}>
          <div style={{ textAlign: 'center', marginBottom: 28 }}>
            <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8, color: 'var(--brand-green)', fontFamily: "'Plus Jakarta Sans', sans-serif", fontWeight: 900, fontSize: '1.5rem', marginBottom: 6 }}>
              <Leaf size={24} /> Annadaan!
            </div>
            <h1 style={{ fontFamily: "'Plus Jakarta Sans', sans-serif", fontWeight: 800, fontSize: '1.3rem', marginTop: 16 }}>
              {isReset ? 'Choose a new password' : 'Forgot your password?'}
            </h1>
            <p style={{ color: 'var(--brand-text-muted)', fontSize: '0.875rem', marginTop: 8 }}>
              {isReset ? 'Create a secure new password for your account.' : 'Enter your account email and we will send a reset link if it matches an account.'}
            </p>
          </div>

          {!isReset && sent ? (
            <div>
              <div role="status" className="alert alert-info">
                If the address matches an account, you may receive a reset link. Check your inbox shortly; if it does not arrive, check your spam folder or try again.
              </div>
              <button
                type="button"
                className="btn btn-secondary"
                onClick={() => setSent(false)}
                style={{ width: '100%', marginTop: 16, justifyContent: 'center' }}
              >
                Send another reset link
              </button>
            </div>
          ) : (
            <form onSubmit={isReset ? handleResetPassword : handleForgotPassword} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              {isReset ? (
                <>
                  <div className="form-group">
                    <label className="form-label">New password</label>
                    <input className="form-input" type="password" autoComplete="new-password" minLength={8} required value={password} onChange={event => setPassword(event.target.value)} />
                    <PasswordRequirements password={password} />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Confirm new password</label>
                    <input className="form-input" type="password" autoComplete="new-password" minLength={8} required value={confirmPassword} onChange={event => setConfirmPassword(event.target.value)} />
                  </div>
                </>
              ) : (
                <div className="form-group">
                  <label className="form-label">Email address</label>
                  <input className="form-input" type="email" autoComplete="email" required value={email} onChange={event => setEmail(event.target.value)} placeholder="you@example.com" />
                </div>
              )}
              <button type="submit" className="btn btn-primary" disabled={loading} style={{ width: '100%', justifyContent: 'center' }}>
                {loading ? <><span className="spinner-xs" /> Please wait…</> : isReset ? 'Update password' : 'Send reset link'}
              </button>
            </form>
          )}

          <p style={{ textAlign: 'center', marginTop: 20, fontSize: '0.875rem', color: 'var(--brand-text-muted)' }}>
            <Link to="/login" style={{ fontWeight: 700 }}>Back to sign in</Link>
          </p>
        </div>
      </div>
    </div>
  );
}