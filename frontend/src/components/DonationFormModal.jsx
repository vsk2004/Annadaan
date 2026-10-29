import { useState } from 'react';
import { donationAPI } from '../api';
import toast from 'react-hot-toast';
import { X, Upload, AlertTriangle, CheckSquare, Square, Info, ChevronRight, ChevronLeft } from 'lucide-react';

const CATEGORIES = [
  { value: 'COOKED_MEALS', label: '🍛 Cooked Meals' },
  { value: 'RICE_BIRYANI', label: '🍚 Rice / Biryani' },
  { value: 'BREAD',        label: '🍞 Bread' },
  { value: 'FRUITS',       label: '🍎 Fruits' },
  { value: 'VEGETABLES',   label: '🥦 Vegetables' },
  { value: 'PACKAGED_FOOD',label: '📦 Packaged Food' },
  { value: 'OTHER',        label: '🍱 Other' },
];
const UNITS    = ['MEALS', 'PACKETS', 'KG', 'LITRES', 'BOXES', 'OTHER'];
const DIETARY  = ['VEGETARIAN', 'NON_VEGETARIAN', 'VEGAN', 'NOT_SPECIFIED'];
const DIETARY_LABELS = { VEGETARIAN: '🌿 Vegetarian', NON_VEGETARIAN: '🥩 Non-Vegetarian', VEGAN: '🌱 Vegan', NOT_SPECIFIED: '❓ Not Specified' };

const SAFETY_CHECKLIST = [
  'Food was prepared in a clean environment with proper hygiene',
  'Food has been stored at the correct temperature (hot food kept hot, cold food kept cold)',
  'Containers are clean, properly sealed, and food-safe',
  'Food has not been left at room temperature for more than 2 hours',
  'I confirm no spoiled, expired, or unsafe items are included',
  'I confirm this food is safe for consumption by others',
];

const EMPTY = {
  foodName: '', description: '', category: '', quantity: '', quantityUnit: 'MEALS',
  servings: '', preparationTime: '', pickupWindowHours: '3', handlingDetails: '',
};

export default function DonationFormModal({ onClose, onSuccess }) {
  const [form, setForm]       = useState(EMPTY);
  const [image, setImage]     = useState(null);
  const [preview, setPreview] = useState(null);
  const [checklist, setChecklist] = useState(Array(SAFETY_CHECKLIST.length).fill(false));
  const [errors, setErrors]   = useState({});
  const [loading, setLoading] = useState(false);
  const [step, setStep]       = useState(1);

  const allChecked = checklist.every(Boolean);

  const set = (key, val) => {
    setForm(f => ({ ...f, [key]: val }));
    setErrors(e => ({ ...e, [key]: '' }));
  };
  const toLocalDateTimeValue = (date) => {
    const pad = (value) => String(value).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
      `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
  };
  const inp = (key, type = 'text', placeholder = '') => ({
    className: 'form-input', type, placeholder, value: form[key],
    onChange: e => set(key, e.target.value),
    style: errors[key] ? { borderColor: '#dc2626' } : {},
  });

  const validateStep1 = () => {
    const e = {};
    if (!form.foodName.trim())      e.foodName = 'Food name is required';
    if (!form.category)             e.category = 'Category is required';
    if (!form.quantity || +form.quantity <= 0) e.quantity = 'Valid quantity required';
    if (!form.preparationTime)      e.preparationTime = 'Preparation time is required';
    else if (new Date(form.preparationTime) > new Date()) e.preparationTime = 'Cannot be in the future';
    if (!form.pickupWindowHours) e.pickupWindowHours = 'Select how long the food will stay available';
    else if (form.preparationTime && new Date(form.preparationTime).getTime() + Number(form.pickupWindowHours) * 60 * 60 * 1000 <= Date.now()) {
      e.pickupWindowHours = 'This pickup window has already ended. Choose a later preparation time.';
    }
    return e;
  };
  const validateStep2 = () => {
    const e = {};
    if (!form.pickupAddress.trim()) e.pickupAddress = 'Address is required';
    if (!form.pickupLatitude)       e.pickupLatitude = 'Latitude is required';
    if (!form.pickupLongitude)      e.pickupLongitude = 'Longitude is required';
    return e;
  };

  const next = () => {
    const errs = step === 1 ? validateStep1() : step === 2 ? validateStep2() : {};
    if (Object.keys(errs).length) { setErrors(errs); return; }
    setErrors({}); setStep(s => s + 1);
  };

  const handleImage = e => {
    const file = e.target.files[0];
    if (!file) return;
    if (file.size > 10 * 1024 * 1024) { toast.error('Image must be under 10 MB'); return; }
    setImage(file); setPreview(URL.createObjectURL(file));
  };

  const handleSubmit = async () => {
    if (!allChecked) { toast.error('Please complete the food safety checklist'); return; }
    const preparationTime = new Date(form.preparationTime);
    const pickupDeadline = new Date(preparationTime.getTime() + Number(form.pickupWindowHours || 3) * 60 * 60 * 1000);
    if (pickupDeadline <= new Date()) {
      toast.error('This pickup window has already ended. Choose a later preparation time.');
      setStep(1);
      return;
    }
    setLoading(true);
    try {
      const data = {
        ...form,
        quantity: parseFloat(form.quantity),
        servings: form.servings ? parseInt(form.servings) : null,
        preparationTime: toLocalDateTimeValue(preparationTime),
        pickupDeadline: toLocalDateTimeValue(pickupDeadline),
        pickupWindowHours: Number(form.pickupWindowHours || 3),
        pickupLatitude:  parseFloat(form.pickupLatitude),
        pickupLongitude: parseFloat(form.pickupLongitude),
        safetyConfirmed: true,
        dietaryType: form.dietaryType || 'NOT_SPECIFIED',
      };
      const fd = new FormData();
      fd.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }));
      if (image) fd.append('image', image);
      await donationAPI.create(fd);
      toast.success('🎉 Donation posted successfully!');
      onSuccess();
    } catch (err) {
      const msg = err.response?.data?.error || 'Failed to post donation';
      toast.error(msg);
    } finally { setLoading(false); }
  };

  const STEP_LABELS = ['Food Details', 'Pickup Location', 'Food Safety'];

  return (
    <div className="modal-overlay" onClick={e => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="modal" style={{ maxWidth: 660 }}>
        {/* Header */}
        <div className="modal-header">
          <div>
            <h2 style={{ fontFamily: "'Plus Jakarta Sans', sans-serif", fontWeight: 800, fontSize: '1.2rem' }}>
              🍽️ Post Food Donation
            </h2>
            <p style={{ fontSize: '0.78rem', color: 'var(--brand-text-muted)', marginTop: 2 }}>
              Step {step} of 3 — {STEP_LABELS[step - 1]}
            </p>
          </div>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 6, borderRadius: 8, color: 'var(--brand-text-muted)' }}>
            <X size={18} />
          </button>
        </div>

        {/* Progress bar */}
        <div style={{ height: 5, background: '#e8e3d0', borderRadius: 3, marginBottom: 24, overflow: 'hidden' }}>
          <div style={{ height: '100%', width: `${(step/3)*100}%`, background: 'var(--brand-green)', transition: 'width 0.3s', borderRadius: 3 }} />
        </div>

        {/* Step 1 — Food Details */}
        {step === 1 && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div className="form-group">
              <label className="form-label">Food Name *</label>
              <input {...inp('foodName', 'text', 'e.g., Home-cooked Dal Rice, Biryani, Wedding Food')} />
              {errors.foodName && <span className="form-error">{errors.foodName}</span>}
            </div>

            <div className="form-group">
              <label className="form-label">Description</label>
              <textarea className="form-textarea" placeholder="Brief description of contents, how it was prepared, etc." rows={2}
                value={form.description} onChange={e => set('description', e.target.value)} />
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <div className="form-group">
                <label className="form-label">Category *</label>
                <select className="form-select" value={form.category} onChange={e => set('category', e.target.value)} style={errors.category ? { borderColor: '#dc2626' } : {}}>
                  <option value="">Select category…</option>
                  {CATEGORIES.map(c => <option key={c.value} value={c.value}>{c.label}</option>)}
                </select>
                {errors.category && <span className="form-error">{errors.category}</span>}
              </div>
              <div className="form-group">
                <label className="form-label">Dietary Type</label>
                <select className="form-select" value={form.dietaryType} onChange={e => set('dietaryType', e.target.value)}>
                  {DIETARY.map(d => <option key={d} value={d}>{DIETARY_LABELS[d]}</option>)}
                </select>
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12 }}>
              <div className="form-group">
                <label className="form-label">Quantity *</label>
                <input {...inp('quantity', 'number', '10')} min="0.1" step="any" />
                {errors.quantity && <span className="form-error">{errors.quantity}</span>}
              </div>
              <div className="form-group">
                <label className="form-label">Unit *</label>
                <select className="form-select" value={form.quantityUnit} onChange={e => set('quantityUnit', e.target.value)}>
                  {UNITS.map(u => <option key={u} value={u}>{u}</option>)}
                </select>
              </div>
              <div className="form-group">
                <label className="form-label">Servings (approx.)</label>
                <input {...inp('servings', 'number', '20')} min="1" />
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <div className="form-group">
                  <label className="form-label">Preparation Time *</label>
                <input {...inp('preparationTime', 'datetime-local')} max={toLocalDateTimeValue(new Date()).slice(0,16)} />
                {errors.preparationTime && <span className="form-error">{errors.preparationTime}</span>}
              </div>
              <div className="form-group">
                <div className="form-group">
                    <label className="form-label">Keep available for {form.pickupWindowHours} {Number(form.pickupWindowHours) === 1 ? 'hour' : 'hours'} *</label>
                    <input
                      className="form-input"
                      type="range"
                      min="1"
                      max="12"
                      step="1"
                      value={form.pickupWindowHours}
                      onChange={e => set('pickupWindowHours', e.target.value)}
                      style={{ accentColor: 'var(--brand-green)' }}
                      aria-label="Pickup availability in hours"
                    />
                    <div style={{ display: 'flex', justifyContent: 'space-between', color: 'var(--brand-text-muted)', fontSize: '0.72rem' }}>
                      <span>1 hour</span><span>12 hours</span>
                    </div>
                  {errors.pickupWindowHours && <span className="form-error">{errors.pickupWindowHours}</span>}
                  <span style={{ display: 'block', marginTop: 6, color: 'var(--brand-text-muted)', fontSize: '0.75rem', lineHeight: 1.5 }}>
                    The deadline is set to the preparation time plus these hours. For example, 3 hours means NGOs can accept it for 3 hours after preparation, even if the deadline falls tomorrow.
                  </span>
                  {form.preparationTime && form.pickupWindowHours && (
                    <span style={{ display: 'block', marginTop: 4, fontSize: '0.78rem', fontWeight: 700 }}>
                      Pickup deadline: {new Date(new Date(form.preparationTime).getTime() + Number(form.pickupWindowHours) * 60 * 60 * 1000).toLocaleString()}
                    </span>
                  )}
                </div>
              </div>
            </div>

            <div className="form-group">
              <label className="form-label">Storage / Handling Details</label>
              <textarea className="form-textarea" placeholder="e.g., Kept covered, refrigerated, in airtight containers…" rows={2}
                value={form.handlingDetails} onChange={e => set('handlingDetails', e.target.value)} />
            </div>

            <div className="form-group">
              <label className="form-label">Allergens / Ingredients (if known)</label>
              <input {...inp('allergenInfo', 'text', 'e.g., Contains peanuts, dairy, gluten…')} />
            </div>

            {/* Image upload */}
            <div className="form-group">
              <label className="form-label">Food Photo (optional)</label>
              <label style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8,
                              padding: 20, border: '2px dashed var(--brand-border)', borderRadius: 12,
                              cursor: 'pointer', background: preview ? 'transparent' : 'var(--brand-cream-dark)',
                              transition: 'var(--transition)' }}
                     onMouseEnter={e => e.currentTarget.style.borderColor = 'var(--brand-green)'}
                     onMouseLeave={e => e.currentTarget.style.borderColor = 'var(--brand-border)'}>
                {preview
                  ? <img src={preview} alt="preview" style={{ maxHeight: 120, borderRadius: 8, objectFit: 'cover' }} />
                  : <><Upload size={22} style={{ color: 'var(--brand-text-muted)' }} />
                    <span style={{ fontSize: '0.82rem', color: 'var(--brand-text-muted)' }}>Click to upload (max 10 MB)</span></>}
                <input type="file" accept="image/*" onChange={handleImage} style={{ display: 'none' }} />
              </label>
            </div>
          </div>
        )}

        {/* Step 2 — Pickup Location */}
        {step === 2 && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div className="alert alert-info">
              <Info size={14} style={{ display: 'inline', marginRight: 6 }} />
              Precise GPS coordinates allow nearby NGOs to find your donation. Enter your latitude/longitude from Google Maps or similar.
            </div>

            <div className="form-group">
              <label className="form-label">Pickup Address *</label>
              <textarea className="form-textarea" rows={2} placeholder="Full address including building, street, landmark…"
                value={form.pickupAddress} onChange={e => set('pickupAddress', e.target.value)}
                style={errors.pickupAddress ? { borderColor: '#dc2626' } : {}} />
              {errors.pickupAddress && <span className="form-error">{errors.pickupAddress}</span>}
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <div className="form-group">
                <label className="form-label">Latitude *</label>
                <input {...inp('pickupLatitude', 'number', '19.0760')} step="any" />
                {errors.pickupLatitude && <span className="form-error">{errors.pickupLatitude}</span>}
              </div>
              <div className="form-group">
                <label className="form-label">Longitude *</label>
                <input {...inp('pickupLongitude', 'number', '72.8777')} step="any" />
                {errors.pickupLongitude && <span className="form-error">{errors.pickupLongitude}</span>}
              </div>
            </div>

            <div className="form-group">
              <label className="form-label">Pickup Instructions</label>
              <textarea className="form-textarea" rows={3} placeholder="When can you be available? Any special instructions for collection?"
                value={form.contactInstructions} onChange={e => set('contactInstructions', e.target.value)} />
            </div>
          </div>
        )}

        {/* Step 3 — Food Safety Checklist */}
        {step === 3 && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <div className="alert alert-warning">
              <AlertTriangle size={14} style={{ display: 'inline', marginRight: 6 }} />
              <strong>Food Safety Confirmation Required.</strong> Check every item before submitting.
            </div>
            {SAFETY_CHECKLIST.map((item, i) => (
              <label key={i} style={{ display: 'flex', alignItems: 'flex-start', gap: 12, padding: '12px 16px',
                                      background: checklist[i] ? '#f0fdf4' : 'var(--brand-cream-dark)',
                                      border: `1px solid ${checklist[i] ? '#bbf7d0' : 'var(--brand-border)'}`,
                                      borderRadius: 10, cursor: 'pointer', transition: 'var(--transition)' }}>
                <div onClick={() => setChecklist(c => { const n=[...c]; n[i]=!n[i]; return n; })} style={{ marginTop: 1, flexShrink: 0 }}>
                  {checklist[i]
                    ? <CheckSquare size={18} style={{ color: 'var(--brand-green)' }} />
                    : <Square size={18} style={{ color: 'var(--brand-text-muted)' }} />}
                </div>
                <span style={{ fontSize: '0.875rem', color: 'var(--brand-text-soft)' }}>{item}</span>
              </label>
            ))}
            {!allChecked && (
              <p style={{ fontSize: '0.8rem', color: 'var(--brand-orange)', marginTop: 4 }}>
                ⚠️ Please confirm all items to submit the donation.
              </p>
            )}
          </div>
        )}

        {/* Footer */}
        <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 24,
                      paddingTop: 16, borderTop: '1px solid var(--brand-border)' }}>
          {step > 1 && (
            <button className="btn btn-secondary" onClick={() => setStep(s => s - 1)} disabled={loading}>
              <ChevronLeft size={14} /> Back
            </button>
          )}
          {step < 3 ? (
            <button className="btn btn-primary" onClick={next}>
              Next <ChevronRight size={14} />
            </button>
          ) : (
            <button className="btn btn-primary" onClick={handleSubmit} disabled={loading || !allChecked}>
              {loading ? <><span className="spinner-xs" /> Posting…</> : '✅ Submit Donation'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
