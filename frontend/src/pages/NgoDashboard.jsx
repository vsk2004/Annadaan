import { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { donationAPI, ngoAPI } from '../api';
import toast from 'react-hot-toast';
import { RefreshCw, LogOut, Radio, Clock, CheckCircle, History, MapPin, User, Leaf, AlertTriangle, Bell } from 'lucide-react';
import DonationCard from '../components/DonationCard';
import ConfirmModal from '../components/ConfirmModal';
import { DonationSkeleton, EmptyState } from '../components/UI';

const POLL_INTERVAL = 30_000;

export default function NgoDashboard() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const pollRef = useRef(null);

  const ngoProfile = user?.ngoProfile;
  const isVerified = ngoProfile?.verificationStatus === 'VERIFIED';
  const isPending  = ngoProfile?.verificationStatus === 'PENDING';
  const isRejected = ngoProfile?.verificationStatus === 'REJECTED';

  const [radius, setRadius]       = useState(5);
  const [nearby, setNearby]       = useState([]);
  const [active, setActive]       = useState([]);
  const [history, setHistory]     = useState([]);
  const [notifications, setNotifications] = useState([]);
  const [stats, setStats]         = useState({});
  const [loadingNearby, setLoadingNearby] = useState(false);
  const [loadingActive, setLoadingActive] = useState(false);
  const [activeTab, setActiveTab] = useState('nearby');

  // Confirm modals
  const [acceptTarget,  setAcceptTarget]  = useState(null);
  const [pickupTarget,  setPickupTarget]  = useState(null);
  const [deliverTarget, setDeliverTarget] = useState(null);
  const [actionLoading, setActionLoading] = useState(false);

  const loadNearby = useCallback(async (r = radius) => {
    if (!isVerified) return;
    setLoadingNearby(true);
    try {
      const { data } = await donationAPI.nearby(r);
      setNearby(data);
    } catch (e) {
      if (e.response?.status !== 401) toast.error(e.response?.data?.error || 'Failed to load nearby donations');
    } finally { setLoadingNearby(false); }
  }, [isVerified, radius]);

  const loadActive = useCallback(async () => {
    if (!isVerified) return;
    setLoadingActive(true);
    try {
      const [aRes, hRes, sRes] = await Promise.all([
        ngoAPI.activeDonations(),
        ngoAPI.history(),
        ngoAPI.getStats(),
      ]);
      setActive(aRes.data);
      setHistory(hRes.data);
      setStats(sRes.data);
    } catch { /* silent */ } finally { setLoadingActive(false); }
  }, [isVerified]);

  const loadNotifications = useCallback(async () => {
    if (!isVerified) return;
    try {
      const { data } = await ngoAPI.notifications();
      setNotifications(data);
    } catch {
      setNotifications([]);
    }
  }, [isVerified]);

  useEffect(() => {
    loadNearby(radius);
    loadActive();
    loadNotifications();
    // Poll every 30s
    pollRef.current = setInterval(() => {
      if (activeTab === 'nearby') loadNearby(radius);
      loadNotifications();
    }, POLL_INTERVAL);
    return () => clearInterval(pollRef.current);
  }, [loadNearby, loadActive, loadNotifications, activeTab, radius]);

  const switchRadius = (r) => {
    setRadius(r);
    loadNearby(r);
  };

  const handleAccept = async ({ note }) => {
    setActionLoading(true);
    try {
      await donationAPI.accept(acceptTarget.id, radius);
      toast.success('✅ Donation accepted! Please coordinate pickup.');
      setAcceptTarget(null);
      await Promise.all([loadNearby(radius), loadActive()]);
    } catch (e) {
      toast.error(e.response?.data?.error || 'Failed to accept donation');
    } finally { setActionLoading(false); }
  };

  const handlePickup = async ({ note }) => {
    setActionLoading(true);
    try {
      await donationAPI.pickup(pickupTarget.id, note);
      toast.success('📦 Marked as Picked Up!');
      setPickupTarget(null);
      loadActive();
    } catch (e) {
      toast.error(e.response?.data?.error || 'Failed to update status');
    } finally { setActionLoading(false); }
  };

  const handleDeliver = async ({ actualServings, completionNotes }) => {
    setActionLoading(true);
    try {
      await donationAPI.deliver(deliverTarget.id, { actualServings: actualServings ? parseInt(actualServings) : null, completionNotes });
      toast.success('🎉 Marked as Delivered! Impact recorded.');
      setDeliverTarget(null);
      loadActive();
      loadNearby(radius);
    } catch (e) {
      toast.error(e.response?.data?.error || 'Failed to update status');
    } finally { setActionLoading(false); }
  };

  return (
    <div className="dashboard-layout">
      {/* Sidebar */}
      <aside className="sidebar">
        <div className="sidebar-logo"><Leaf size={20} /> Annadaan!</div>

        <div style={{ fontSize: '0.7rem', fontWeight: 700, color: 'var(--brand-text-muted)', letterSpacing: '0.08em', textTransform: 'uppercase', marginBottom: 8 }}>Menu</div>
        <nav className="sidebar-nav">
          <button className={`sidebar-item ${activeTab === 'nearby' ? 'active' : ''}`} onClick={() => setActiveTab('nearby')}>
            <Radio size={15} /> Nearby Food
          </button>
          <button className={`sidebar-item ${activeTab === 'active' ? 'active' : ''}`} onClick={() => setActiveTab('active')}>
            <Clock size={15} /> Active Pickups
            {active.length > 0 && <span style={{ marginLeft: 'auto', background: 'var(--brand-orange)', color: 'white', fontSize: '0.68rem', fontWeight: 800, padding: '1px 6px', borderRadius: 999 }}>{active.length}</span>}
          </button>
          <button className={`sidebar-item ${activeTab === 'history' ? 'active' : ''}`} onClick={() => setActiveTab('history')}>
            <History size={15} /> History
          </button>
          <Link to="/ngo/profile" className="sidebar-item" style={{ textDecoration: 'none' }}><User size={15} /> Profile</Link>
        </nav>

        {/* Location info */}
        {ngoProfile && (
          <div style={{ marginTop: 16, padding: '10px 12px', background: 'rgba(45,106,79,0.06)', border: '1px solid rgba(45,106,79,0.12)', borderRadius: 10 }}>
            <div style={{ fontSize: '0.68rem', fontWeight: 700, textTransform: 'uppercase', color: 'var(--brand-text-muted)', marginBottom: 6 }}>Location</div>
            {ngoProfile.latitude && ngoProfile.longitude
              ? <div style={{ fontSize: '0.78rem', color: 'var(--brand-text-soft)', display: 'flex', alignItems: 'center', gap: 4 }}>
                  <MapPin size={11} style={{ color: 'var(--brand-green)' }} />
                  {(+ngoProfile.latitude).toFixed(4)}, {(+ngoProfile.longitude).toFixed(4)}
                </div>
              : <div style={{ fontSize: '0.78rem', color: '#dc2626' }}>⚠️ Location not set — update profile</div>}
          </div>
        )}

        <div style={{ marginTop: 'auto', borderTop: '1px solid var(--brand-border)', paddingTop: 14 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 9, padding: '10px 12px', borderRadius: 10, background: 'rgba(45,106,79,0.06)', border: '1px solid rgba(45,106,79,0.12)', marginBottom: 8 }}>
            <div style={{ width: 30, height: 30, borderRadius: '50%', background: '#4f46e5', color: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 800, fontSize: '0.85rem' }}>
              {user?.name?.[0]?.toUpperCase()}
            </div>
            <div>
              <div style={{ fontSize: '0.8rem', fontWeight: 700, lineHeight: 1.2 }}>{user?.name}</div>
              <div style={{ fontSize: '0.68rem', color: 'var(--brand-text-muted)' }}>NGO</div>
            </div>
          </div>
          <button className="sidebar-item" onClick={logout} style={{ color: '#dc2626', width: '100%' }}><LogOut size={15} /> Logout</button>
        </div>
      </aside>

      {/* Main */}
      <main className="dashboard-content">
        {/* Header */}
        <div className="page-header">
          <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: 12 }}>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
                <h1 className="page-title">🏥 {ngoProfile?.organizationName || user?.name}</h1>
                <span className={`badge ${isVerified ? 'badge-green' : isPending ? 'badge-pending' : 'badge-red'}`}>
                  {isVerified ? '✓ Verified' : isPending ? '⏳ Pending Verification' : '✗ Rejected'}
                </span>
              </div>
              <p className="page-subtitle">Discover and collect surplus food near you</p>
            </div>

            {isVerified && (
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <div className="radius-selector">
                  {[5, 10].map(r => (
                    <button key={r} className={`radius-btn ${radius === r ? 'active' : ''}`} onClick={() => switchRadius(r)}>
                      {r} km
                    </button>
                  ))}
                </div>
                <button className="btn btn-secondary btn-sm" onClick={() => loadNearby(radius)} disabled={loadingNearby}>
                  <RefreshCw size={13} className={loadingNearby ? 'animate-spin' : ''} />
                </button>
              </div>
            )}
          </div>
        </div>

        {/* Verification notices */}
        {isPending && (
          <div className="alert alert-warning">
            <AlertTriangle size={14} style={{ display: 'inline', marginRight: 6 }} />
            <strong>Verification Pending.</strong> Your NGO is under review. Only verified NGOs can accept donations.
            You'll be notified once approved. Contact us at <a href="mailto:verify@annadaan.in">verify@annadaan.in</a>.
          </div>
        )}
        {isRejected && (
          <div className="alert alert-error">
            <AlertTriangle size={14} style={{ display: 'inline', marginRight: 6 }} />
            <strong>Verification Rejected.</strong> Please update your profile and contact us at <a href="mailto:support@annadaan.in">support@annadaan.in</a>.
          </div>
        )}

        {isVerified && (
          <>
            {notifications.length > 0 && (
              <div className="card" style={{ marginBottom: 20, border: '1px solid rgba(45,106,79,0.14)' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
                  <Bell size={16} style={{ color: 'var(--brand-green)' }} />
                  <h3 style={{ margin: 0, fontSize: '0.95rem' }}>New donor alerts</h3>
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                  {notifications.slice(0, 3).map(n => (
                    <div key={n.id} style={{ padding: '12px 14px', background: 'rgba(45,106,79,0.04)', borderRadius: 10, border: '1px solid rgba(45,106,79,0.08)' }}>
                      <div style={{ fontWeight: 700, marginBottom: 4 }}>{n.title}</div>
                      <div style={{ color: 'var(--brand-text-soft)', fontSize: '0.8rem', lineHeight: 1.5 }}>{n.message}</div>
                      <div style={{ marginTop: 6, fontSize: '0.72rem', color: 'var(--brand-text-muted)' }}>
                        {n.donorName} • {n.distanceKm ? `${n.distanceKm} km away` : 'nearby'}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Stats */}
            <div className="grid-4" style={{ marginBottom: 28 }}>
              {[
                { label: 'Available Nearby', value: nearby.length, color: '#2d6a4f', bg: 'rgba(45,106,79,0.08)', emoji: '📍' },
                { label: 'Active Pickups',   value: active.length,  color: 'var(--brand-orange)', bg: 'rgba(231,111,81,0.08)', emoji: '🚚' },
                { label: 'Completed',        value: history.length, color: '#166534', bg: 'rgba(22,101,52,0.08)', emoji: '✅' },
                { label: 'Search Radius',    value: `${radius} km`, color: '#7c3aed', bg: 'rgba(124,58,237,0.08)', emoji: '📡' },
              ].map((s, i) => (
                <div key={i} className="stat-card" style={{ animationDelay: `${i*0.08}s` }}>
                  <div className="stat-icon" style={{ background: s.bg, fontSize: '1.1rem', border: 'none' }}>{s.emoji}</div>
                  <div className="stat-value" style={{ color: s.color }}>{s.value}</div>
                  <div className="stat-label">{s.label}</div>
                </div>
              ))}
            </div>

            {/* Tabs */}
            <div className="card">
              <div className="tabs" style={{ marginBottom: 18 }}>
                {[
                  { key: 'nearby',  label: `📍 Nearby (${nearby.length})` },
                  { key: 'active',  label: `🚚 Active (${active.length})` },
                  { key: 'history', label: `✅ History (${history.length})` },
                ].map(t => (
                  <button key={t.key} className={`tab ${activeTab === t.key ? 'active' : ''}`} onClick={() => setActiveTab(t.key)}>
                    {t.label}
                  </button>
                ))}
              </div>

              {/* Nearby */}
              {activeTab === 'nearby' && (
                loadingNearby
                  ? <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>{[1,2,3].map(i => <DonationSkeleton key={i} />)}</div>
                  : nearby.length === 0
                    ? <EmptyState icon="📍" title={`No food available within ${radius} km`} />
                  : <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                      <p style={{ fontSize: '0.78rem', color: 'var(--brand-text-muted)', marginBottom: 4 }}>
                        Auto-refreshes every 30s · Last refresh: just now
                      </p>
                      {nearby.map(d => (
                        <DonationCard key={d.id} donation={d} role="NGO"
                          onAccept={() => setAcceptTarget(d)}
                          viewPath={`/ngo/donations/${d.id}`} />
                      ))}
                    </div>
              )}

              {/* Active */}
              {activeTab === 'active' && (
                loadingActive
                  ? <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>{[1,2].map(i => <DonationSkeleton key={i} />)}</div>
                  : active.length === 0
                  ? <EmptyState icon="🚚" title="No active pickups" subtitle="Accept a nearby donation to start a pickup." />
                  : <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                      {active.map(d => (
                        <DonationCard key={d.id} donation={d} role="NGO"
                          onPickup={() => setPickupTarget(d)}
                          onDeliver={() => setDeliverTarget(d)}
                          viewPath={`/ngo/donations/${d.id}`} />
                      ))}
                    </div>
              )}

              {/* History */}
              {activeTab === 'history' && (
                history.length === 0
                  ? <EmptyState icon="✅" title="No deliveries yet" subtitle="Complete your first food rescue to see it here." />
                  : <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                      {history.map(d => (
                        <DonationCard key={d.id} donation={d} role="NGO" viewPath={`/ngo/donations/${d.id}`} />
                      ))}
                    </div>
              )}
            </div>
          </>
        )}
      </main>

      {/* Confirm: Accept */}
      {acceptTarget && (
        <ConfirmModal title="Accept Donation" variant="info" confirmLabel="✅ Accept Donation" loading={actionLoading}
          message={`Accept "${acceptTarget.foodName}"? You commit to picking it up before ${new Date(acceptTarget.pickupDeadline).toLocaleString()}.`}
          onConfirm={handleAccept} onClose={() => setAcceptTarget(null)} />
      )}

      {/* Confirm: Pickup */}
      {pickupTarget && (
        <ConfirmModal title="Confirm Pickup" variant="warning" confirmLabel="📦 Confirm Pickup" loading={actionLoading}
          message={`Confirm you have physically collected "${pickupTarget.foodName}" from the donor?`}
          extraFields={[{ key: 'note', label: 'Notes (optional)', type: 'text', placeholder: 'Any pickup notes…' }]}
          onConfirm={handlePickup} onClose={() => setPickupTarget(null)} />
      )}

      {/* Confirm: Deliver */}
      {deliverTarget && (
        <ConfirmModal title="Confirm Delivery" variant="info" confirmLabel="🎉 Mark Delivered" loading={actionLoading}
          message={`Confirm that "${deliverTarget.foodName}" has been successfully delivered to recipients?`}
          extraFields={[
            { key: 'actualServings',  label: 'Actual servings delivered', type: 'number', placeholder: 'e.g., 25' },
            { key: 'completionNotes', label: 'Completion notes (optional)', type: 'textarea', placeholder: 'Any notes about the delivery…' },
          ]}
          onConfirm={handleDeliver} onClose={() => setDeliverTarget(null)} />
      )}
    </div>
  );
}
