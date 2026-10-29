import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Navbar from '../components/Navbar';
import { Leaf } from 'lucide-react';
import toast from 'react-hot-toast';

export default function LoginPage() {
  const { login, user } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ email: '', password: '' });
  const [errors, setErrors] = useState({});
  const [loading, setLoading] = useState(false);

  // Redirect if already logged in
  if (user) {
    navigate(user.role === 'DONOR' ? '/donor/dashboard' : '/ngo/dashboard', { replace: true });
    return null;
  }

  const validate = () => {
    const e = {};
    if (!form.email)    e.email    = 'Email is required';
    if (!form.password) e.password = 'Password is required';
    return e;
  };

  const handleSubmit = async (ev) => {
    ev.preventDefault();
    const errs = validate();
    if (Object.keys(errs).length) { setErrors(errs); return; }
    setLoading(true);
    try {
      const u = await login(form.email.trim(), form.password);
      toast.success(`Welcome back, ${u.name?.split(' ')[0]}! 👋`);
      navigate(u.role === 'DONOR' ? '/donor/dashboard' : '/ngo/dashboard', { replace: true });
    } catch (e) {
      const message = e.response?.data?.message || e.response?.data?.error ||
        (!e.response ? 'Unable to reach the server. Check your connection and try again.' :
          e.response.status >= 500 ? 'Sign-in is temporarily unavailable. Please try again.' :
            'Unable to sign in. Please check your details and try again.');
      toast.error(message);
    } finally { setLoading(false); }
  };

  return (
    <div>
      <Navbar />
      <div style={{ minHeight: '85vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'linear-gradient(165deg, var(--brand-cream) 0%, #d8f3dc 100%)', padding: 24 }}>
        <div style={{ background: 'white', borderRadius: 20, padding: '40px 36px', width: '100%', maxWidth: 440, boxShadow: '0 8px 40px rgba(0,0,0,0.1)', border: '1px solid var(--brand-border)' }}>
          {/* Logo */}
          <div style={{ textAlign: 'center', marginBottom: 28 }}>
            <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8, color: 'var(--brand-green)', fontFamily: "'Plus Jakarta Sans', sans-serif", fontWeight: 900, fontSize: '1.5rem', marginBottom: 6 }}>
              <Leaf size={24} /> Annadaan!
            </div>
            <p style={{ color: 'var(--brand-text-muted)', fontSize: '0.875rem' }}>Good Food. Shared Hope.</p>
          </div>

          <h1 style={{ fontFamily: "'Plus Jakarta Sans', sans-serif", fontWeight: 800, fontSize: '1.3rem', marginBottom: 24, textAlign: 'center' }}>Sign In</h1>

          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div className="form-group">
              <label className="form-label">Email address</label>
              <input className="form-input" type="email" placeholder="you@example.com"
                value={form.email} onChange={e => setForm(f => ({ ...f, email: e.target.value }))}
                style={errors.email ? { borderColor: '#dc2626' } : {}} />
              {errors.email && <span className="form-error">{errors.email}</span>}
            </div>
            <div className="form-group">
              <label className="form-label">Password</label>
              <input className="form-input" type="password" placeholder="••••••••"
                value={form.password} onChange={e => setForm(f => ({ ...f, password: e.target.value }))}
                style={errors.password ? { borderColor: '#dc2626' } : {}} />
              {errors.password && <span className="form-error">{errors.password}</span>}
            </div>
            <button type="submit" className="btn btn-primary" style={{ width: '100%', justifyContent: 'center', marginTop: 4 }} disabled={loading}>
              {loading ? <><span className="spinner-xs" /> Signing in…</> : 'Sign In →'}
            </button>
            <Link to="/reset-password" style={{ alignSelf: 'center', fontSize: '0.82rem', fontWeight: 700 }}>Forgot password?</Link>
          </form>

          <p style={{ textAlign: 'center', marginTop: 20, fontSize: '0.875rem', color: 'var(--brand-text-muted)' }}>
            Don't have an account?{' '}
            <Link to="/register" style={{ fontWeight: 700 }}>Create one →</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
