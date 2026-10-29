// StatusBadge.jsx — maps every DonationStatus to a styled badge
const STATUS_CONFIG = {
  AVAILABLE:  { label: 'Available',  bg: '#16a34a22', color: '#22c55e', border: '#22c55e44' },
  ACCEPTED:   { label: 'Accepted',   bg: '#1d4ed822', color: '#60a5fa', border: '#60a5fa44' },
  PICKED_UP:  { label: 'Picked Up',  bg: '#ea580c22', color: '#fb923c', border: '#fb923c44' },
  DELIVERED:  { label: 'Delivered',  bg: '#15803d22', color: '#4ade80', border: '#4ade8044' },
  CANCELLED:  { label: 'Cancelled',  bg: '#6b728022', color: '#9ca3af', border: '#9ca3af44' },
  EXPIRED:    { label: 'Expired',    bg: '#dc262622', color: '#f87171', border: '#f8717144' },
};

export default function StatusBadge({ status, size = 'md' }) {
  const cfg = STATUS_CONFIG[status] || STATUS_CONFIG.EXPIRED;
  const pad = size === 'sm' ? '2px 8px' : '4px 12px';
  const fs  = size === 'sm' ? '0.7rem'  : '0.78rem';
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: 5,
      background: cfg.bg, color: cfg.color,
      border: `1px solid ${cfg.border}`,
      padding: pad, borderRadius: 999,
      fontWeight: 700, fontSize: fs, letterSpacing: '0.02em',
      userSelect: 'none', whiteSpace: 'nowrap',
    }}>
      <span style={{ width: 6, height: 6, borderRadius: '50%', background: cfg.color, flexShrink: 0 }} />
      {cfg.label}
    </span>
  );
}

export { STATUS_CONFIG };
