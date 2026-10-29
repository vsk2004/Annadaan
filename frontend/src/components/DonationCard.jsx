import { MapPin, Clock, CheckCircle, XCircle, Truck, Package, AlertTriangle, ChevronRight } from 'lucide-react';
import { formatDistanceToNow, format } from 'date-fns';
import StatusBadge from './StatusBadge';
import { Link } from 'react-router-dom';

const CATEGORY_EMOJI = {
  COOKED_MEALS: '🍛', RICE_BIRYANI: '🍚', BREAD: '🍞',
  FRUITS: '🍎', VEGETABLES: '🥦', PACKAGED_FOOD: '📦', OTHER: '🍱',
};
const UNIT_LABEL = { MEALS: 'meals', PACKETS: 'packets', KG: 'kg', LITRES: 'L', BOXES: 'boxes', OTHER: '' };
const DIETARY_BADGE = {
  VEGETARIAN:     { label: '🌿 Veg',     color: 'var(--green-400)', bg: 'rgba(34,197,94,0.1)',   border: 'rgba(34,197,94,0.25)' },
  NON_VEGETARIAN: { label: '🥩 Non-Veg', color: '#f87171',          bg: 'rgba(239,68,68,0.1)',   border: 'rgba(239,68,68,0.25)' },
  VEGAN:          { label: '🌱 Vegan',   color: '#4ade80',          bg: 'rgba(74,222,128,0.1)',  border: 'rgba(74,222,128,0.25)' },
};

export default function DonationCard({ donation: d, role, onCancel, onAccept, onPickup, onDeliver, viewPath }) {
  if (!d) return null;
  const isExpired     = d.pickupDeadline && new Date(d.pickupDeadline) < new Date();
  const timeLeft      = d.pickupDeadline && !isExpired
                          ? formatDistanceToNow(new Date(d.pickupDeadline), { addSuffix: false }) + ' left'
                          : 'Deadline passed';
  const dietary       = DIETARY_BADGE[d.dietaryType];
  const imageUrl      = d.imageUrl ? `http://localhost:8080${d.imageUrl}` : null;
  const isAssignedNgo = role === 'NGO' && d.assignedNgoId != null;

  return (
    <div style={{
      display: 'flex', gap: 0, background: 'var(--bg-secondary)',
      border: '1px solid var(--border)', borderRadius: 16, overflow: 'hidden',
      transition: 'transform 0.15s, border-color 0.15s',
    }}
      onMouseEnter={e => { e.currentTarget.style.transform = 'translateY(-2px)'; e.currentTarget.style.borderColor = 'rgba(255,255,255,0.12)'; }}
      onMouseLeave={e => { e.currentTarget.style.transform = 'none'; e.currentTarget.style.borderColor = 'var(--border)'; }}>

      {/* Thumbnail */}
      <div style={{ width: 96, flexShrink: 0, background: 'var(--bg-card-hover)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '2.2rem' }}>
        {imageUrl
          ? <img src={imageUrl} alt={d.foodName} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
          : (CATEGORY_EMOJI[d.category] || '🍱')}
      </div>

      {/* Body */}
      <div style={{ flex: 1, padding: '12px 14px', display: 'flex', flexDirection: 'column', gap: 6, minWidth: 0 }}>
        {/* Title row */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
          <span style={{ fontWeight: 700, fontSize: '0.95rem', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {d.foodName}
          </span>
          <StatusBadge status={d.status} size="sm" />
          {dietary && (
            <span style={{ fontSize: '0.68rem', fontWeight: 700, padding: '2px 7px', borderRadius: 999,
                           color: dietary.color, background: dietary.bg, border: `1px solid ${dietary.border}` }}>
              {dietary.label}
            </span>
          )}
        </div>

        {/* Meta row */}
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, fontSize: '0.775rem', color: 'var(--text-muted)' }}>
          <span>{d.category?.replace('_', ' ')} · {d.quantity} {UNIT_LABEL[d.quantityUnit] || d.quantityUnit}
            {d.servings ? ` · ~${d.servings} servings` : ''}</span>
        </div>

        {/* Info chips */}
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, fontSize: '0.76rem', color: 'var(--text-muted)' }}>
          {d.pickupAddress && (
            <span style={{ display: 'flex', alignItems: 'center', gap: 3 }}>
              <MapPin size={11} />
              {d.pickupAddress.length > 45 ? d.pickupAddress.slice(0, 42) + '…' : d.pickupAddress}
            </span>
          )}
          <span style={{ display: 'flex', alignItems: 'center', gap: 3, color: isExpired ? '#f87171' : 'inherit' }}>
            <Clock size={11} style={{ color: isExpired ? '#f87171' : 'var(--orange-400)' }} />
            {timeLeft}
          </span>
          {d.safetyConfirmed && (
            <span style={{ display: 'flex', alignItems: 'center', gap: 3, color: 'var(--green-400)' }}>
              <CheckCircle size={11} /> Safety confirmed
            </span>
          )}
        </div>

        {/* NGO / Allergen info row */}
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center' }}>
          {d.distanceKm != null && (
            <span style={{ fontSize: '0.72rem', fontWeight: 700, color: 'var(--green-400)',
                           background: 'rgba(34,197,94,0.1)', border: '1px solid rgba(34,197,94,0.2)',
                           padding: '2px 8px', borderRadius: 999, display: 'flex', alignItems: 'center', gap: 4 }}>
              <MapPin size={10} /> {d.distanceKm} km away
            </span>
          )}
          {d.assignedNgoName && (
            <span style={{ fontSize: '0.72rem', color: '#818cf8', background: 'rgba(129,140,248,0.1)',
                           border: '1px solid rgba(129,140,248,0.2)', padding: '2px 8px', borderRadius: 999 }}>
              🏥 {d.assignedNgoName}
            </span>
          )}
          {d.allergenInfo && (
            <span style={{ fontSize: '0.7rem', color: 'var(--orange-400)', background: 'rgba(249,115,22,0.08)',
                           border: '1px solid rgba(249,115,22,0.2)', borderRadius: 6, padding: '2px 7px',
                           display: 'flex', alignItems: 'center', gap: 4 }}>
              <AlertTriangle size={10} /> {d.allergenInfo}
            </span>
          )}
        </div>
      </div>

      {/* Actions */}
      <div style={{ display: 'flex', flexDirection: 'column', justifyContent: 'center', gap: 6,
                    padding: '12px 14px', borderLeft: '1px solid var(--border)', flexShrink: 0 }}>

        {/* View details */}
        {viewPath && (
          <Link to={viewPath} className="btn btn-secondary btn-sm" style={{ textDecoration: 'none' }}>
            <ChevronRight size={13} /> View
          </Link>
        )}

        {/* Donor: cancel while AVAILABLE */}
        {role === 'DONOR' && d.status === 'AVAILABLE' && (
          <button className="btn btn-danger btn-sm" onClick={() => onCancel?.(d)}>
            <XCircle size={13} /> Cancel
          </button>
        )}

        {/* NGO: accept AVAILABLE */}
        {role === 'NGO' && d.status === 'AVAILABLE' && !isExpired && (
          <button className="btn btn-primary btn-sm" onClick={() => onAccept?.(d)}>
            ✅ Accept
          </button>
        )}

        {/* NGO: pickup after ACCEPTED */}
        {role === 'NGO' && d.status === 'ACCEPTED' && (
          <button className="btn btn-warning btn-sm" onClick={() => onPickup?.(d)}>
            <Truck size={13} /> Picked Up
          </button>
        )}

        {/* NGO: deliver after PICKED_UP */}
        {role === 'NGO' && d.status === 'PICKED_UP' && (
          <button className="btn btn-primary btn-sm" onClick={() => onDeliver?.(d)}>
            <Package size={13} /> Delivered
          </button>
        )}
      </div>
    </div>
  );
}
