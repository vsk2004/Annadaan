import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { donationAPI } from '../api';
import toast from 'react-hot-toast';
import { Plus, RefreshCw, BarChart2, Package, CheckCircle, Truck, LogOut, Leaf } from 'lucide-react';
import DonationCard from '../components/DonationCard';
import DonationFormModal from '../components/DonationFormModal';
import ConfirmModal from '../components/ConfirmModal';
import { DonationSkeleton, EmptyState } from '../components/UI';

export default function DonorDashboard() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [stats, setStats]       = useState({});
  const [donations, setDonations] = useState([]);
  const [loading, setLoading]   = useState(true);
  const [activeTab, setActiveTab] = useState('all');
  const [showForm, setShowForm] = useState(false);
  const [cancelTarget, setCancelTarget] = useState(null);
  const [cancelling, setCancelling] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [sRes, dRes] = await Promise.all([
        donationAPI.mine().then(r => r.data),
        donationAPI.mine().then(r => r.data),
      ]);
      // Compute stats from list
      const all = dRes;
      setDonations(all);
      const count = s => all.filter(d => d.status === s).length;
      setStats({
        total: all.length,
        available: count('AVAILABLE'),
        accepted: count('ACCEPTED') + count('PICKED_UP'),
        delivered: count('DELIVERED'),
        cancelled: count('CANCELLED'),
      });
    } catch {
      toast.error('Failed to load donations');
    } finally { setLoading(false); }
  }, []);

  useEffect(() => { load(); }, [load]);

  const filtered = activeTab === 'all'
    ? donations
    : donations.filter(d => d.status === activeTab.toUpperCase());

  const handleCancelConfirm = async ({ reason }) => {
    setCancelling(true);
    try {
      await donationAPI.cancel(cancelTarget.id, reason);
      toast.success('Donation cancelled');
      setCancelTarget(null);
      load();
    } catch (e) {
      toast.error(e.response?.data?.error || 'Could not cancel');
    } finally { setCancelling(false); }
  };

  const statCards = [
    { label: 'Total Posted', value: stats.total ?? '—', icon: Package, color: '#2d6a4f', bg: 'rgba(45,106,79,0.08)' },
    { label: 'Available', value: stats.available ?? '—', icon: CheckCircle, color: '#16a34a', bg: 'rgba(22,163,74,0.08)' },
    { label: 'In Progress', value: stats.accepted ?? '—', icon: Truck, color: 'var(--brand-orange)', bg: 'rgba(231,111,81,0.08)' },
    { label: 'Delivered', value: stats.delivered ?? '—', icon: '🎉', isEmoji: true, color: '#166534', bg: 'rgba(22,101,52,0.08)' },
  ];

  const TABS = [
    { key: 'all', label: 'All' },
    { key: 'AVAILABLE', label: 'Available' },
    { key: 'ACCEPTED', label: 'Accepted' },
    { key: 'PICKED_UP', label: 'Picked Up' },
    { key: 'DELIVERED', label: 'Delivered' },
    { key: 'CANCELLED', label: 'Cancelled' },
  ];

  return (
    <div className="dashboard-layout">
      {/* Sidebar */}
      <aside className="sidebar">
        <div className="sidebar-logo"><Leaf size={20} /> Annadaan!</div>

        <div style={{ fontSize: '0.7rem', fontWeight: 700, color: 'var(--brand-text-muted)', letterSpacing: '0.08em', textTransform: 'uppercase', marginBottom: 8 }}>Menu</div>
        <nav className="sidebar-nav">
          <button className="sidebar-item active"><BarChart2 size={15} /> Dashboard</button>
          <button className="sidebar-item" onClick={() => setShowForm(true)}><Plus size={15} /> Post Donation</button>
        </nav>

        <div style={{ marginTop: 'auto', borderTop: '1px solid var(--brand-border)', paddingTop: 14 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 9, padding: '10px 12px', borderRadius: 10,
                        background: 'rgba(45,106,79,0.06)', border: '1px solid rgba(45,106,79,0.12)', marginBottom: 8 }}>
            <div style={{ width: 30, height: 30, borderRadius: '50%', background: 'var(--brand-green)',
                          color: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center',
                          fontWeight: 800, fontSize: '0.85rem' }}>
              {user?.name?.[0]?.toUpperCase()}
            </div>
            <div>
              <div style={{ fontSize: '0.8rem', fontWeight: 700, lineHeight: 1.2 }}>{user?.name}</div>
              <div style={{ fontSize: '0.68rem', color: 'var(--brand-text-muted)' }}>Donor</div>
            </div>
          </div>
          <button className="sidebar-item" onClick={logout} style={{ color: '#dc2626', width: '100%' }}><LogOut size={15} /> Logout</button>
        </div>
      </aside>

      {/* Main */}
      <main className="dashboard-content">
        <div className="page-header" style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: 12 }}>
          <div>
            <h1 className="page-title">Welcome back, {user?.name?.split(' ')[0]}! 👋</h1>
            <p className="page-subtitle">Manage your food donations and track their journey</p>
          </div>
          <div style={{ display: 'flex', gap: 10 }}>
            <button className="btn btn-secondary btn-sm" onClick={load} disabled={loading}>
              <RefreshCw size={13} className={loading ? 'animate-spin' : ''} /> Refresh
            </button>
            <button className="btn btn-primary" onClick={() => setShowForm(true)}>
              <Plus size={15} /> Post Donation
            </button>
          </div>
        </div>

        {/* Stats */}
        <div className="grid-4" style={{ marginBottom: 28 }}>
          {statCards.map((s, i) => {
            const Icon = s.icon;
            return (
              <div key={i} className="stat-card" style={{ animationDelay: `${i * 0.08}s` }}>
                <div className="stat-icon" style={{ background: s.bg }}>
                  {s.isEmoji
                    ? <span style={{ fontSize: '1.1rem' }}>{s.icon}</span>
                    : <Icon size={18} style={{ color: s.color }} />}
                </div>
                <div className="stat-value" style={{ color: s.color }}>{loading ? '—' : s.value}</div>
                <div className="stat-label">{s.label}</div>
              </div>
            );
          })}
        </div>

        {/* Donations table */}
        <div className="card">
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
            <h2 style={{ fontWeight: 700, fontSize: '0.95rem' }}>Donation History</h2>
          </div>

          <div className="tabs" style={{ marginBottom: 16 }}>
            {TABS.map(t => (
              <button key={t.key} className={`tab ${activeTab === t.key ? 'active' : ''}`} onClick={() => setActiveTab(t.key)}>
                {t.label}
              </button>
            ))}
          </div>

          {loading ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {[1,2,3].map(i => <DonationSkeleton key={i} />)}
            </div>
          ) : filtered.length === 0 ? (
            <EmptyState icon="🍽️" title="No donations in this category"
              subtitle="Post your first donation to start making an impact"
              action={<button className="btn btn-primary btn-sm" onClick={() => setShowForm(true)}><Plus size={13} /> Post Donation</button>} />
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {filtered.map(d => (
                <DonationCard key={d.id} donation={d} role="DONOR"
                  onCancel={() => setCancelTarget(d)}
                  viewPath={`/donor/donations/${d.id}`} />
              ))}
            </div>
          )}
        </div>
      </main>

      {showForm && <DonationFormModal onClose={() => setShowForm(false)} onSuccess={() => { setShowForm(false); load(); }} />}
      {cancelTarget && (
        <ConfirmModal title="Cancel Donation" variant="danger" confirmLabel="Cancel Donation" loading={cancelling}
          message={`Are you sure you want to cancel "${cancelTarget.foodName}"? This action cannot be undone.`}
          extraFields={[{ key: 'reason', label: 'Reason', type: 'text', placeholder: 'Reason for cancellation…', required: false }]}
          onConfirm={handleCancelConfirm} onClose={() => setCancelTarget(null)} />
      )}
    </div>
  );
}
