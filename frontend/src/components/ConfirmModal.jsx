import { AlertTriangle, X } from 'lucide-react';
import { useState } from 'react';

/**
 * ConfirmModal — requires explicit user confirmation before a destructive action.
 *
 * Props:
 *   title, message, confirmLabel, onConfirm, onClose,
 *   variant: 'danger' | 'warning' | 'info'
 *   extraFields: [{ key, label, type, placeholder, required }]
 */
export default function ConfirmModal({
  title = 'Confirm Action',
  message,
  confirmLabel = 'Confirm',
  onConfirm,
  onClose,
  variant = 'warning',
  extraFields = [],
  loading = false,
}) {
  const [fieldValues, setFieldValues] = useState({});
  const [errors, setErrors] = useState({});

  const colors = {
    danger:  { btn: 'btn-danger',   icon: '#f87171', bg: 'rgba(248,113,113,0.1)', border: '#f8717133' },
    warning: { btn: 'btn-warning',  icon: 'var(--orange-400)', bg: 'rgba(249,115,22,0.1)', border: '#fb923c33' },
    info:    { btn: 'btn-primary',  icon: '#60a5fa', bg: 'rgba(96,165,250,0.1)', border: '#60a5fa33' },
  };
  const clr = colors[variant] || colors.warning;

  const handleConfirm = () => {
    const errs = {};
    extraFields.forEach(f => {
      if (f.required && !fieldValues[f.key]?.trim()) errs[f.key] = `${f.label} is required`;
    });
    if (Object.keys(errs).length) { setErrors(errs); return; }
    onConfirm(fieldValues);
  };

  return (
    <div className="modal-overlay" onClick={e => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="modal" style={{ maxWidth: 440 }}>
        {/* Header */}
        <div className="modal-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <div style={{ width: 40, height: 40, borderRadius: '50%', background: clr.bg,
                          border: `1px solid ${clr.border}`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <AlertTriangle size={18} style={{ color: clr.icon }} />
            </div>
            <h3 style={{ fontWeight: 700, fontSize: '1rem', margin: 0 }}>{title}</h3>
          </div>
          <button onClick={onClose} style={{ background: 'none', border: 'none', color: 'var(--text-muted)',
                                             cursor: 'pointer', padding: 4, borderRadius: 6 }}>
            <X size={18} />
          </button>
        </div>

        {message && (
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem', lineHeight: 1.6, margin: '0 0 16px' }}>
            {message}
          </p>
        )}

        {extraFields.map(f => (
          <div key={f.key} className="form-group" style={{ marginBottom: 14 }}>
            <label className="form-label">{f.label}{f.required ? ' *' : ''}</label>
            {f.type === 'textarea' ? (
              <textarea className="form-textarea" rows={3} placeholder={f.placeholder}
                value={fieldValues[f.key] || ''}
                onChange={e => setFieldValues(v => ({ ...v, [f.key]: e.target.value }))}
                style={errors[f.key] ? { borderColor: '#f87171' } : {}} />
            ) : (
              <input className="form-input" type={f.type || 'text'} placeholder={f.placeholder}
                value={fieldValues[f.key] || ''}
                onChange={e => setFieldValues(v => ({ ...v, [f.key]: e.target.value }))}
                style={errors[f.key] ? { borderColor: '#f87171' } : {}} />
            )}
            {errors[f.key] && <span className="form-error">{errors[f.key]}</span>}
          </div>
        ))}

        <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 20,
                      paddingTop: 16, borderTop: '1px solid var(--border)' }}>
          <button className="btn btn-secondary" onClick={onClose} disabled={loading}>Cancel</button>
          <button className={`btn ${clr.btn}`} onClick={handleConfirm} disabled={loading}>
            {loading
              ? <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <span className="spinner-xs" /> Processing…
                </span>
              : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
