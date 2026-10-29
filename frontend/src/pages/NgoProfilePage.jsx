import { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { ngoAPI } from '../api';
import { Leaf, LogOut, User, Save } from 'lucide-react';
import { Link } from 'react-router-dom';
import toast from 'react-hot-toast';

export default function NgoProfilePage() {
  const { user, logout, refreshUser } = useAuth();
  const [form, setForm]     = useState({});
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    ngoAPI.getProfile().then(({ data }) => {
      setForm({
        organizationName: data.organizationName || '',
        organizationType: data.organizationType || '',
        contactPerson:    data.contactPerson    || '',
        address:          data.address          || '',
        city:             data.city             || '',
        state:            data.state            || '',
        postalCode:       data.postalCode       || '',
        latitude:         data.latitude         || '',
        longitude:        data.longitude        || '',
      });
    }).catch((e) => {
      const message = e.response?.data?.message || e.response?.data?.error ||
        (!e.response ? 'Unable to reach the server. Check your connection and try again.' : 'Failed to load NGO profile.');
      toast.error(message);
    });
  }, []);

  const handleSave = async () => {
    setSaving(true);
    try {
      await ngoAPI.updateProfile({
        ...form,
        latitude:  form.latitude  ? parseFloat(form.latitude)  : null,
        longitude: form.longitude ? parseFloat(form.longitude) : null,
      });
      await refreshUser();
      toast.success('Profile updated!');
    } catch (e) {
      toast.error(e.response?.data?.error || 'Failed to save');
    } finally { setSaving(false); }
  };

  const set = (k, v) => setForm(f => ({ ...f, [k]: v }));
  const inp = (k, placeholder = '', type = 'text') => ({
    className: 'form-input', type, placeholder,
    value: form[k] || '', onChange: e => set(k, e.target.value),
  });

  return (
    <div className="dashboard-layout">
      <aside className="sidebar">
        <div className="sidebar-logo"><Leaf size={20} /> Annadaan!</div>
        <nav className="sidebar-nav">
          <Link to="/ngo/dashboard" className="sidebar-item" style={{ textDecoration: 'none' }}>← Dashboard</Link>
          <button className="sidebar-item active"><User size={15} /> Profile</button>
        </nav>
        <div style={{ marginTop: 'auto', borderTop: '1px solid var(--brand-border)', paddingTop: 14 }}>
          <button className="sidebar-item" onClick={logout} style={{ color: '#dc2626', width: '100%' }}><LogOut size={15} /> Logout</button>
        </div>
      </aside>

      <main className="dashboard-content">
        <div className="page-header">
          <h1 className="page-title">NGO Profile</h1>
          <p className="page-subtitle">Keep your organization info and location up to date</p>
        </div>

        <div className="card">
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
            <div className="form-group">
              <label className="form-label">Organization Name</label>
              <input {...inp('organizationName', 'Your NGO name')} />
            </div>
            <div className="form-group">
              <label className="form-label">Organization Type</label>
              <input {...inp('organizationType', 'e.g., Food Bank, Shelter, School')} />
            </div>
            <div className="form-group">
              <label className="form-label">Contact Person</label>
              <input {...inp('contactPerson', 'Name of contact person')} />
            </div>
            <div className="form-group">
              <label className="form-label">Full Address</label>
              <input {...inp('address', 'Street address')} />
            </div>
            <div className="form-group">
              <label className="form-label">City</label>
              <input {...inp('city', 'City')} />
            </div>
            <div className="form-group">
              <label className="form-label">State</label>
              <input {...inp('state', 'State')} />
            </div>
            <div className="form-group">
              <label className="form-label">Postal Code</label>
              <input {...inp('postalCode', '400001')} />
            </div>
          </div>

          <hr style={{ margin: '20px 0', border: 'none', borderTop: '1px solid var(--brand-border)' }} />

          <h3 style={{ fontWeight: 700, marginBottom: 12, fontSize: '0.9rem', color: 'var(--brand-green)' }}>📍 Location (for radius matching)</h3>
          <div className="alert alert-info" style={{ marginBottom: 14 }}>
            Your latitude/longitude is used to find nearby food donations. Get it from Google Maps or similar.
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
            <div className="form-group">
              <label className="form-label">Latitude *</label>
              <input {...inp('latitude', '19.0760', 'number')} step="any" />
            </div>
            <div className="form-group">
              <label className="form-label">Longitude *</label>
              <input {...inp('longitude', '72.8777', 'number')} step="any" />
            </div>
          </div>

          <div style={{ marginTop: 20 }}>
            <button className="btn btn-primary" onClick={handleSave} disabled={saving}>
              {saving ? <><span className="spinner-xs" /> Saving…</> : <><Save size={15} /> Save Profile</>}
            </button>
          </div>
        </div>
      </main>
    </div>
  );
}
