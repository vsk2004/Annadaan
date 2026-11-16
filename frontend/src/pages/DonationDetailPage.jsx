import { useState, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { donationAPI } from '../api';
import StatusBadge from '../components/StatusBadge';
import StatusTracker from '../components/StatusTracker';
import { format } from 'date-fns';
import { MapPin, Clock, ArrowLeft, AlertTriangle, CheckCircle, Mail, RotateCw } from 'lucide-react';
import { Skeleton } from '../components/UI';
import toast from 'react-hot-toast';

const CATEGORY_LABELS = {
  COOKED_MEALS: '🍛 Cooked Meals', RICE_BIRYANI: '🍚 Rice/Biryani', BREAD: '🍞 Bread',
  FRUITS: '🍎 Fruits', VEGETABLES: '🥦 Vegetables', PACKAGED_FOOD: '📦 Packaged Food', OTHER: '🍱 Other',
};
const UNIT_LABELS = { MEALS: 'meals', PACKETS: 'packets', KG: 'kg', LITRES: 'L', BOXES: 'boxes', OTHER: '' };

function Detail({ label, value }) {
  if (!value) return null;
  return (
    <div style={{ display: 'flex', gap: 8 }}>
      <span style={{ fontWeight: 600, fontSize: '0.8rem', color: 'var(--brand-text-muted)', minWidth: 140, flexShrink: 0 }}>{label}</span>
      <span style={{ fontSize: '0.875rem', color: 'var(--brand-text-soft)' }}>{value}</span>
    </div>
  );
}

function Ts({ label, ts }) {
  if (!ts) return null;
  return <Detail label={label} value={format(new Date(ts), 'dd MMM yyyy, HH:mm')} />;
}

export default function DonationDetailPage() {
  const { id } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [donation, setDonation] = useState(null);
  const [history, setHistory]   = useState([]);
  const [loading, setLoading]   = useState(true);
  const [retrying, setRetrying] = useState(false);

  const backPath = user?.role === 'DONOR' ? '/donor/dashboard' : '/ngo/dashboard';

  const fetchDetails = async () => {
    try {
      const [dRes, hRes] = await Promise.all([
        donationAPI.getById(id),
        donationAPI.history(id),
      ]);
      setDonation(dRes.data);
      setHistory(hRes.data);
    } catch (e) {
      toast.error('Could not load donation');
      navigate(backPath);
    } finally { setLoading(false); }
  };

  useEffect(() => {
    fetchDetails();
  }, [id]);

  const handleRetryEmail = async () => {
    setRetrying(true);
    try {
      const { data } = await donationAPI.retryEmail(id);
      setDonation(data);
      if (data.emailNotificationStatus === 'SENT') {
        toast.success('Notification email sent successfully!');
      } else {
        toast.error('Notification email delivery failed: ' + (data.emailFailureReason || 'Please check Brevo settings'));
      }
    } catch (err) {
      toast.error(err.response?.data?.error || 'Failed to retry notification email');
    } finally {
      setRetrying(false);
    }
  };

  if (loading) return (
    <div style={{ padding: 32 }}>
      <Skeleton height={40} width="40%" style={{ marginBottom: 24 }} />
      <Skeleton height={300} />
    </div>
  );
  if (!donation) return null;

  const d = donation;
  const imageUrl = d.imageUrl ? `http://localhost:8080${d.imageUrl}` : null;

  return (
    <div style={{ maxWidth: 760, margin: '0 auto', padding: 32 }}>
      {/* Back */}
      <Link to={backPath} style={{ display: 'inline-flex', alignItems: 'center', gap: 6, marginBottom: 20,
                                   color: 'var(--brand-text-muted)', fontWeight: 500, fontSize: '0.875rem' }}>
        <ArrowLeft size={16} /> Back to Dashboard
      </Link>

      <div className="card" style={{ marginBottom: 20 }}>
        {/* Title + Status */}
        <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 16, marginBottom: 16, flexWrap: 'wrap' }}>
          <div>
            <h1 style={{ fontFamily: "'Plus Jakarta Sans', sans-serif", fontSize: '1.4rem', fontWeight: 800, marginBottom: 8 }}>
              {d.foodName}
            </h1>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
              <StatusBadge status={d.status} />
              {d.distanceKm && (
                <span style={{ fontSize: '0.78rem', color: 'var(--brand-green)', fontWeight: 700,
                               background: 'rgba(45,106,79,0.08)', border: '1px solid rgba(45,106,79,0.2)',
                               padding: '3px 10px', borderRadius: 999 }}>
                  📍 {d.distanceKm} km away
                </span>
              )}
            </div>
          </div>
          {imageUrl && (
            <img src={imageUrl} alt={d.foodName} style={{ width: 100, height: 80, objectFit: 'cover', borderRadius: 10, border: '1px solid var(--brand-border)' }} />
          )}
        </div>

        {/* Progress tracker */}
        <StatusTracker status={d.status} />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 20 }}>
        {/* Food details */}
        <div className="card">
          <h3 style={{ fontWeight: 700, fontSize: '0.9rem', marginBottom: 14, color: 'var(--brand-green)' }}>🍛 Food Details</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <Detail label="Category"    value={CATEGORY_LABELS[d.category]} />
            <Detail label="Quantity"    value={`${d.quantity} ${UNIT_LABELS[d.quantityUnit] || d.quantityUnit}`} />
            <Detail label="Servings"    value={d.servings ? `~${d.servings} people` : null} />
            <Detail label="Dietary"     value={d.dietaryType?.replace('_', ' ')} />
            <Detail label="Allergens"   value={d.allergenInfo} />
            <Detail label="Storage"     value={d.handlingDetails} />
            <Detail label="Description" value={d.description} />
            {d.safetyConfirmed && (
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: 'var(--brand-green)', fontSize: '0.82rem', fontWeight: 600 }}>
                <CheckCircle size={14} /> Food safety confirmed by donor
              </div>
            )}
          </div>
        </div>

        {/* Pickup details */}
        <div className="card">
          <h3 style={{ fontWeight: 700, fontSize: '0.9rem', marginBottom: 14, color: 'var(--brand-green)' }}>📍 Pickup Details</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <Detail label="Address"           value={d.pickupAddress} />
            {d.pickupLatitude && <Detail label="Coordinates" value={`${(+d.pickupLatitude).toFixed(5)}, ${(+d.pickupLongitude).toFixed(5)}`} />}
            <Detail label="Contact info"      value={d.contactInstructions} />
            <Ts     label="Prepared at"       ts={d.preparationTime} />
            <Ts     label="Pickup deadline"   ts={d.pickupDeadline} />
          </div>
        </div>
      </div>

      {/* Participants & Timestamps */}
      <div className="card" style={{ marginBottom: 20 }}>
        <h3 style={{ fontWeight: 700, fontSize: '0.9rem', marginBottom: 14, color: 'var(--brand-green)' }}>👥 Participants & Timeline</h3>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          <Detail label="Donor"         value={d.donorName} />
          <Detail label="Assigned NGO"  value={d.assignedNgoName || (d.status === 'AVAILABLE' ? 'Not yet accepted' : '—')} />
          {d.assignedNgoContactPerson && <Detail label="NGO Representative" value={d.assignedNgoContactPerson} />}
          {d.assignedNgoEmail && <Detail label="NGO Email" value={d.assignedNgoEmail} />}
          {d.assignedNgoPhone && <Detail label="NGO Phone" value={d.assignedNgoPhone} />}
          {d.assignedNgoAddress && <Detail label="NGO Address" value={d.assignedNgoAddress} />}
          <Ts     label="Posted at"     ts={d.createdAt} />
          <Ts     label="Accepted at"   ts={d.acceptedAt} />
          <Ts     label="Picked up at"  ts={d.pickedUpAt} />
          <Ts     label="Delivered at"  ts={d.deliveredAt} />
          <Ts     label="Cancelled at"  ts={d.cancelledAt} />
          <Detail label="Cancellation reason" value={d.cancellationReason} />
          {d.completionNotes && <Detail label="Completion notes" value={d.completionNotes} />}
          {d.actualServingsDelivered && <Detail label="Actual servings delivered" value={`${d.actualServingsDelivered} people`} />}
        </div>

        {/* Email Notification Status Banner (for accepted donations) */}
        {d.status !== 'AVAILABLE' && d.status !== 'CANCELLED' && (
          <div style={{ marginTop: 16, paddingTop: 14, borderTop: '1px solid var(--brand-border)' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 10 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: '0.85rem' }}>
                <Mail size={16} style={{ color: d.emailNotificationStatus === 'SENT' ? '#16a34a' : (d.emailNotificationStatus === 'FAILED' ? '#dc2626' : 'var(--brand-green)') }} />
                <span>
                  {d.emailNotificationStatus === 'SENT' ? (
                    <span style={{ color: '#16a34a', fontWeight: 600 }}>✓ Donor notification email sent successfully</span>
                  ) : d.emailNotificationStatus === 'FAILED' ? (
                    <span style={{ color: '#dc2626', fontWeight: 600 }}>
                      ⚠️ Email notification delivery failed {d.emailFailureReason ? `(${d.emailFailureReason})` : ''}
                    </span>
                  ) : (
                    <span style={{ color: 'var(--brand-text-muted)' }}>Email notification status: Pending / Processing</span>
                  )}
                </span>
              </div>
              {d.emailNotificationStatus !== 'SENT' && (
                <button
                  className="btn btn-secondary btn-sm"
                  onClick={handleRetryEmail}
                  disabled={retrying}
                  style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: '0.8rem' }}
                >
                  <RotateCw size={14} className={retrying ? 'animate-spin' : ''} />
                  {retrying ? 'Retrying...' : 'Retry Email'}
                </button>
              )}
            </div>
          </div>
        )}
      </div>

      {/* Status History (audit trail) */}
      {history.length > 0 && (
        <div className="card">
          <h3 style={{ fontWeight: 700, fontSize: '0.9rem', marginBottom: 14, color: 'var(--brand-green)' }}>📋 Status Audit Trail</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {history.map((h, i) => (
              <div key={h.id} style={{ display: 'flex', gap: 12, alignItems: 'flex-start',
                                       paddingBottom: 10, borderBottom: i < history.length-1 ? '1px solid var(--brand-border)' : 'none' }}>
                <div style={{ width: 10, height: 10, borderRadius: '50%', background: 'var(--brand-green)', marginTop: 5, flexShrink: 0 }} />
                <div style={{ flex: 1 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                    {h.previousStatus && <StatusBadge status={h.previousStatus} size="sm" />}
                    {h.previousStatus && <span style={{ fontSize: '0.8rem', color: 'var(--brand-text-muted)' }}>→</span>}
                    <StatusBadge status={h.newStatus} size="sm" />
                  </div>
                  <div style={{ fontSize: '0.78rem', color: 'var(--brand-text-muted)', marginTop: 4 }}>
                    by <strong>{h.changedByName}</strong> · {format(new Date(h.changedAt), 'dd MMM yyyy, HH:mm')}
                    {h.note && <span> · {h.note}</span>}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
