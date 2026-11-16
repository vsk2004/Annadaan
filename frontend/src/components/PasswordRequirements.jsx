export default function PasswordRequirements({ password = '' }) {
  const rules = [
    { label: '8+ chars', met: password.length >= 8 },
    { label: 'A-Z', met: /[A-Z]/.test(password) },
    { label: 'a-z', met: /[a-z]/.test(password) },
    { label: '0-9', met: /[0-9]/.test(password) },
    { label: 'Special', met: /[^A-Za-z0-9]/.test(password) },
  ];

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 6,
        marginTop: 6,
        padding: '5px 10px',
        background: 'rgba(45, 106, 79, 0.05)',
        borderRadius: 8,
        border: '1px solid rgba(45, 106, 79, 0.14)',
        whiteSpace: 'nowrap',
        overflowX: 'auto',
      }}
    >
      <span
        style={{
          fontSize: '0.7rem',
          fontWeight: 700,
          color: 'var(--brand-green)',
          display: 'inline-flex',
          alignItems: 'center',
          gap: 4,
          flexShrink: 0,
        }}
      >
        Must have:
      </span>
      <div style={{ display: 'flex', alignItems: 'center', gap: 5, flexShrink: 0 }}>
        {rules.map((rule) => (
          <span
            key={rule.label}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 3,
              fontSize: '0.68rem',
              fontWeight: rule.met ? 700 : 500,
              padding: '2px 7px',
              borderRadius: 12,
              transition: 'all 0.18s ease',
              background: rule.met ? '#d8f3dc' : '#ffffff',
              color: rule.met ? '#1b4332' : '#6b7280',
              border: `1px solid ${rule.met ? '#74c69d' : '#e5e7eb'}`,
              boxShadow: rule.met ? '0 1px 3px rgba(45, 106, 79, 0.15)' : 'none',
            }}
          >
            <span style={{ fontSize: '0.65rem' }}>{rule.met ? '✓' : '•'}</span>
            <span>{rule.label}</span>
          </span>
        ))}
      </div>
    </div>
  );
}
