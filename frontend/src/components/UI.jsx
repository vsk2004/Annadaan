// Skeleton loading placeholder
export function Skeleton({ width = '100%', height = 20, radius = 8, style = {} }) {
  return (
    <div className="skeleton" style={{ width, height, borderRadius: radius, ...style }} />
  );
}

// Empty state
export function EmptyState({ icon = '📭', title, subtitle, action }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center',
                  justifyContent: 'center', padding: '48px 24px', textAlign: 'center' }}>
      <div style={{ fontSize: '3rem', marginBottom: 16, filter: 'grayscale(0.3)' }}>{icon}</div>
      {title && <p style={{ fontWeight: 700, fontSize: '1rem', margin: '0 0 6px' }}>{title}</p>}
      {subtitle && <p style={{ color: 'var(--text-muted)', fontSize: '0.875rem', margin: 0 }}>{subtitle}</p>}
      {action && <div style={{ marginTop: 16 }}>{action}</div>}
    </div>
  );
}

// Error alert
export function ErrorAlert({ message, onDismiss }) {
  if (!message) return null;
  return (
    <div style={{ background: 'rgba(248,113,113,0.1)', border: '1px solid #f8717133',
                  borderRadius: 12, padding: '12px 16px', color: '#f87171',
                  fontSize: '0.875rem', display: 'flex', alignItems: 'flex-start', gap: 10 }}>
      <span>⚠️</span>
      <span style={{ flex: 1 }}>{message}</span>
      {onDismiss && (
        <button onClick={onDismiss} style={{ background: 'none', border: 'none',
                                             color: '#f87171', cursor: 'pointer', padding: 0, fontSize: '1rem' }}>×</button>
      )}
    </div>
  );
}

// Pagination
export function Pagination({ page, totalPages, onPageChange }) {
  if (totalPages <= 1) return null;
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, marginTop: 24 }}>
      <button className="btn btn-secondary btn-sm" disabled={page === 0} onClick={() => onPageChange(page - 1)}>
        ← Prev
      </button>
      <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
        Page {page + 1} of {totalPages}
      </span>
      <button className="btn btn-secondary btn-sm" disabled={page >= totalPages - 1} onClick={() => onPageChange(page + 1)}>
        Next →
      </button>
    </div>
  );
}

// Card skeleton (donation-sized)
export function DonationSkeleton() {
  return (
    <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border)',
                  borderRadius: 16, overflow: 'hidden', display: 'flex', height: 100 }}>
      <Skeleton width={100} height="100%" radius={0} />
      <div style={{ flex: 1, padding: 16, display: 'flex', flexDirection: 'column', gap: 10 }}>
        <Skeleton width="60%" height={16} />
        <Skeleton width="40%" height={12} />
        <Skeleton width="80%" height={12} />
      </div>
    </div>
  );
}
