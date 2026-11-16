import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Navbar from '../components/Navbar';
import { Leaf } from 'lucide-react';
import toast from 'react-hot-toast';
import { validatePassword } from '../utils/validation';
import PasswordRequirements from '../components/PasswordRequirements';

export default function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [role, setRole] = useState('DONOR');
  const [form, setForm] = useState({
    name: '', email: '', phone: '', password: '', confirmPassword: '',
    address: '', city: '', state: '', postalCode: '', latitude: '', longitude: '',
    // NGO fields
    organizationName: '', organizationType: '', contactPerson: '', registrationNumber: '', ngoRegistrationInfo: '',
  });
  const [errors, setErrors] = useState({});
  const [loading, setLoading] = useState(false);
  const [step, setStep] = useState(1); // 1 = account, 2 = address, 3 = NGO-specific

  const set = (k, v) => { setForm(f => ({ ...f, [k]: v })); setErrors(e => ({ ...e, [k]: '' })); };
  const inp = (k, type = 'text', placeholder = '') => ({
    className: 'form-input', type, placeholder,
    value: form[k], onChange: e => set(k, e.target.value),
    style: errors[k] ? { borderColor: '#dc2626' } : {},
  });

  const totalSteps = role === 'NGO' ? 3 : 2;

  const validateStep1 = () => {
    const e = {};
    if (!form.name.trim())    e.name    = 'Name is required';
    if (!form.email.trim())   e.email   = 'Email is required';
    else if (!/\S+@\S+\.\S+/.test(form.email)) e.email = 'Invalid email';
    if (!form.phone.trim())   e.phone   = 'Phone is required';
    else if (!/^[+]?[0-9()\-\s]{7,20}$/.test(form.phone.trim())) e.phone = 'Invalid phone number';
    const pwdErr = validatePassword(form.password);
    if (pwdErr) e.password = pwdErr;
    if (form.password !== form.confirmPassword) e.confirmPassword = 'Passwords do not match';
    return e;
  };
  const validateStep2 = () => {
    const e = {};
    if (!form.latitude)  e.latitude  = 'Latitude is required for location matching';
    if (!form.longitude) e.longitude = 'Longitude is required for location matching';
    return e;
  };

  const handleNext = () => {
    const errs = step === 1 ? validateStep1() : step === 2 ? validateStep2() : {};
    if (Object.keys(errs).length) { setErrors(errs); return; }
    setErrors({}); setStep(s => s + 1);
  };

  const handleSubmit = async () => {
    setLoading(true);
    try {
      const payload = {
        ...form,
        email: form.email.trim(),
        phone: form.phone.trim().replace(/\s+/g, ' '),
        role,
        latitude:  form.latitude  ? parseFloat(form.latitude)  : null,
        longitude: form.longitude ? parseFloat(form.longitude) : null,
      };
      const u = await register(payload);
      toast.success(`Welcome to Annadaan!, ${u.name?.split(' ')[0]}! 🌿`);
      if (u.role === 'NGO') {
        toast('Your NGO is under review. You\'ll be able to accept donations once verified.', { icon: 'ℹ️', duration: 5000 });
      }
      navigate(u.role === 'DONOR' ? '/donor/dashboard' : '/ngo/dashboard', { replace: true });
    } catch (e) {
      const message =
        e.response?.data?.message ||
        e.response?.data?.error ||
        e.response?.data?.fields?.[0]?.message ||
        e.message ||
        'Registration failed. Please check your details.';

      toast.error(message.length > 80 ? message.slice(0, 80) + '…' : message);
    } finally { setLoading(false); }
  };

  const STEP_LABELS = role === 'NGO'
    ? ['Account Details', 'Location', 'NGO Information']
    : ['Account Details', 'Location'];

  return (
    <div>
      <Navbar />
      <div style={{ minHeight: '85vh', display: 'flex', alignItems: 'flex-start', justifyContent: 'center', background: 'linear-gradient(165deg, var(--brand-cream) 0%, #d8f3dc 100%)', padding: '32px 24px' }}>
        <div style={{ background: 'white', borderRadius: 20, padding: '36px', width: '100%', maxWidth: 520, boxShadow: '0 8px 40px rgba(0,0,0,0.1)', border: '1px solid var(--brand-border)' }}>
          {/* Logo */}
          <div style={{ textAlign: 'center', marginBottom: 20 }}>
            <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8, color: 'var(--brand-green)', fontFamily: "'Plus Jakarta Sans', sans-serif", fontWeight: 900, fontSize: '1.4rem' }}>
              <Leaf size={22} /> Annadaan!
            </div>
          </div>

          <h1 style={{ fontFamily: "'Plus Jakarta Sans', sans-serif", fontWeight: 800, fontSize: '1.2rem', marginBottom: 6, textAlign: 'center' }}>Create an Account</h1>
          <p style={{ textAlign: 'center', fontSize: '0.8rem', color: 'var(--brand-text-muted)', marginBottom: 20 }}>
            Step {step} of {totalSteps} — {STEP_LABELS[step - 1]}
          </p>

          {/* Role toggle */}
          {step === 1 && (
            <div style={{ display: 'flex', background: 'var(--brand-cream-dark)', borderRadius: 10, padding: 4, marginBottom: 20 }}>
              {['DONOR', 'NGO'].map(r => (
                <button key={r} onClick={() => { setRole(r); setStep(1); }}
                  style={{ flex: 1, padding: '9px', border: 'none', borderRadius: 7, cursor: 'pointer',
                           fontWeight: 700, fontSize: '0.85rem', transition: 'var(--transition)',
                           background: role === r ? 'var(--brand-green)' : 'transparent',
                           color: role === r ? 'white' : 'var(--brand-text-muted)' }}>
                  {r === 'DONOR' ? '🍛 Donor' : '🏥 NGO'}
                </button>
              ))}
            </div>
          )}

          {/* Progress */}
          <div style={{ height: 4, background: '#e8e3d0', borderRadius: 3, marginBottom: 22, overflow: 'hidden' }}>
            <div style={{ height: '100%', width: `${(step/totalSteps)*100}%`, background: 'var(--brand-green)', transition: 'width 0.3s', borderRadius: 3 }} />
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            {/* Step 1 — Account */}
            {step === 1 && (
              <>
                <div className="form-group">
                  <label className="form-label">{role === 'NGO' ? 'Organization / Contact Name *' : 'Full Name *'}</label>
                  <input {...inp('name', 'text', 'Your full name')} />
                  {errors.name && <span className="form-error">{errors.name}</span>}
                </div>
                <div className="form-group">
                  <label className="form-label">Email *</label>
                  <input {...inp('email', 'email', 'you@example.com')} />
                  {errors.email && <span className="form-error">{errors.email}</span>}
                </div>
                <div className="form-group">
                  <label className="form-label">Phone *</label>
                  <input {...inp('phone', 'tel', '+91 98765 43210')} />
                  {errors.phone && <span className="form-error">{errors.phone}</span>}
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                  <div className="form-group">
                    <label className="form-label">Password *</label>
                    <input {...inp('password', 'password', '••••••••')} />
                    {errors.password && <span className="form-error">{errors.password}</span>}
                  </div>
                  <div className="form-group">
                    <label className="form-label">Confirm Password *</label>
                    <input {...inp('confirmPassword', 'password', '••••••••')} />
                    {errors.confirmPassword && <span className="form-error">{errors.confirmPassword}</span>}
                  </div>
                </div>
                <PasswordRequirements password={form.password} />
              </>
            )}

            {/* Step 2 — Location */}
            {step === 2 && (
              <>
                <div className="alert alert-info">
                  ℹ️ Your coordinates enable our GPS-based matching. Get them from Google Maps by right-clicking your location.
                </div>
                <div className="form-group">
                  <label className="form-label">Address</label>
                  <input {...inp('address', 'text', 'Street address')} />
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                  <div className="form-group"><label className="form-label">City</label><input {...inp('city', 'text', 'Mumbai')} /></div>
                  <div className="form-group"><label className="form-label">State</label><input {...inp('state', 'text', 'Maharashtra')} /></div>
                  <div className="form-group"><label className="form-label">Postal Code</label><input {...inp('postalCode', 'text', '400001')} /></div>
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                  <div className="form-group">
                    <label className="form-label">Latitude *</label>
                    <input {...inp('latitude', 'number', '19.0760')} step="any" />
                    {errors.latitude && <span className="form-error">{errors.latitude}</span>}
                  </div>
                  <div className="form-group">
                    <label className="form-label">Longitude *</label>
                    <input {...inp('longitude', 'number', '72.8777')} step="any" />
                    {errors.longitude && <span className="form-error">{errors.longitude}</span>}
                  </div>
                </div>
              </>
            )}

            {/* Step 3 — NGO Info */}
            {step === 3 && role === 'NGO' && (
              <>
                <div className="alert alert-warning">
                  🔍 NGO accounts require verification before accepting donations. You'll be notified by email once approved.
                </div>
                <div className="form-group">
                  <label className="form-label">Organization Name</label>
                  <input {...inp('organizationName', 'text', 'Hope Foundation')} />
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                  <div className="form-group">
                    <label className="form-label">Organization Type</label>
                    <input {...inp('organizationType', 'text', 'Food Bank, Shelter, School…')} />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Contact Person</label>
                    <input {...inp('contactPerson', 'text', 'Name of coordinator')} />
                  </div>
                </div>
                <div className="form-group">
                  <label className="form-label">NGO Registration Number (if applicable)</label>
                  <input {...inp('registrationNumber', 'text', 'e.g., NGO-2024-XXXXX')} />
                </div>
                <div className="form-group">
                  <label className="form-label">Verification Information</label>
                  <textarea className="form-textarea" rows={3} placeholder="Describe your organization, who you serve, and any credentials…"
                    value={form.ngoRegistrationInfo} onChange={e => set('ngoRegistrationInfo', e.target.value)} />
                </div>
              </>
            )}
          </div>

          {/* Footer actions */}
          <div style={{ display: 'flex', gap: 10, marginTop: 22 }}>
            {step > 1 && (
              <button className="btn btn-secondary" onClick={() => setStep(s => s - 1)} style={{ flex: '0 0 auto' }}>← Back</button>
            )}
            {step < totalSteps ? (
              <button className="btn btn-primary" onClick={handleNext} style={{ flex: 1, justifyContent: 'center' }}>
                Continue →
              </button>
            ) : (
              <button className="btn btn-primary" onClick={handleSubmit} disabled={loading} style={{ flex: 1, justifyContent: 'center' }}>
                {loading ? <><span className="spinner-xs" /> Creating account…</> : '✅ Create Account'}
              </button>
            )}
          </div>

          <p style={{ textAlign: 'center', marginTop: 16, fontSize: '0.85rem', color: 'var(--brand-text-muted)' }}>
            Already have an account? <Link to="/login" style={{ fontWeight: 700 }}>Sign in →</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
