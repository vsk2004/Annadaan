import { CheckCircle, Circle, XCircle, Clock } from 'lucide-react';

const STAGES = [
  { key: 'AVAILABLE', label: 'Food Posted' },
  { key: 'ACCEPTED',  label: 'Accepted by NGO' },
  { key: 'PICKED_UP', label: 'Picked Up' },
  { key: 'DELIVERED', label: 'Delivered' },
];

const STAGE_ORDER = { AVAILABLE: 0, ACCEPTED: 1, PICKED_UP: 2, DELIVERED: 3 };
const TERMINAL    = { CANCELLED: true, EXPIRED: true };

export default function StatusTracker({ status }) {
  const isTerminal = TERMINAL[status];
  const currentIdx = STAGE_ORDER[status] ?? -1;

  return (
    <div style={{ padding: '16px 0' }}>
      {isTerminal ? (
        <div style={{ display: 'flex', alignItems: 'center', gap: 10,
                      background: status === 'EXPIRED' ? 'rgba(248,113,113,0.1)' : 'rgba(156,163,175,0.1)',
                      border: `1px solid ${status === 'EXPIRED' ? '#f8717144' : '#9ca3af44'}`,
                      borderRadius: 12, padding: '14px 20px' }}>
          <XCircle size={20} style={{ color: status === 'EXPIRED' ? '#f87171' : '#9ca3af', flexShrink: 0 }} />
          <div>
            <div style={{ fontWeight: 700, fontSize: '0.9rem', color: status === 'EXPIRED' ? '#f87171' : '#9ca3af' }}>
              {status === 'EXPIRED' ? 'Donation Expired' : 'Donation Cancelled'}
            </div>
            <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginTop: 2 }}>
              {status === 'EXPIRED'
                ? 'The pickup deadline passed before this donation was claimed.'
                : 'This donation was cancelled and is no longer active.'}
            </div>
          </div>
        </div>
      ) : (
        <div style={{ display: 'flex', alignItems: 'center', gap: 0, overflowX: 'auto', paddingBottom: 4 }}>
          {STAGES.map((stage, idx) => {
            const done    = idx < currentIdx;
            const current = idx === currentIdx;
            const pending = idx > currentIdx;

            return (
              <div key={stage.key} style={{ display: 'flex', alignItems: 'center', flex: '0 0 auto' }}>
                {/* Stage node */}
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6, minWidth: 90 }}>
                  <div style={{
                    width: 36, height: 36, borderRadius: '50%', display: 'flex',
                    alignItems: 'center', justifyContent: 'center', transition: 'all 0.3s',
                    background: done    ? '#16a34a' : current ? 'var(--gradient-brand)' : 'transparent',
                    border: `2px solid ${done ? '#16a34a' : current ? '#22c55e' : '#374151'}`,
                    boxShadow: current  ? '0 0 12px rgba(34,197,94,0.4)' : 'none',
                  }}>
                    {done
                      ? <CheckCircle size={18} style={{ color: 'white' }} />
                      : current
                        ? <Clock size={16} style={{ color: 'white' }} />
                        : <Circle size={16} style={{ color: '#4b5563' }} />}
                  </div>
                  <span style={{
                    fontSize: '0.7rem', fontWeight: current ? 700 : 500, textAlign: 'center',
                    color: done ? '#22c55e' : current ? 'var(--text-primary)' : 'var(--text-muted)',
                    lineHeight: 1.3, maxWidth: 80,
                  }}>
                    {stage.label}
                  </span>
                </div>

                {/* Connector line */}
                {idx < STAGES.length - 1 && (
                  <div style={{
                    flex: 1, height: 2, minWidth: 24,
                    background: idx < currentIdx ? '#16a34a' : '#1f2937',
                    margin: '-18px 4px 0',
                    transition: 'background 0.3s',
                  }} />
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
