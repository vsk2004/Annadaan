import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Leaf, CheckCircle, AlertTriangle, Heart, Users, Clock } from 'lucide-react';
import Navbar from '../components/Navbar';

const STEPS = [
  { n: 1, emoji: '📋', title: 'Register & Set Location', body: 'Create a donor or NGO account. Set your precise location (GPS) so our system can match donations to NGOs within your area.' },
  { n: 2, emoji: '🍛', title: 'Donors Post Food', body: 'Donors submit surplus food with details: category, quantity, preparation time, pickup deadline, and a food safety checklist confirmation.' },
  { n: 3, emoji: '🔍', title: 'NGOs Discover Nearby Food', body: 'Verified NGOs see available donations within 5 km or 10 km of their location, calculated using GPS coordinates. Distance is shown on each card.' },
  { n: 4, emoji: '✅', title: 'NGO Accepts & Picks Up', body: 'An NGO accepts a donation — the system atomically assigns it so two NGOs cannot claim the same food. The NGO then confirms pickup.' },
  { n: 5, emoji: '🚚', title: 'Food is Delivered', body: 'After pickup, the NGO marks delivery complete. The full donation history and timestamps are preserved for transparency.' },
];

const SAFETY = [
  'Food must be stored at correct temperature (hot food ≥ 60°C, cold food ≤ 4°C)',
  'Containers must be clean, sealed, and food-safe',
  'Cooked food should not sit at room temperature for more than 2 hours',
  'Donors must complete and confirm the food safety checklist before posting',
  'Clearly disclose all allergens and ingredients when known',
  'Do not donate food that is spoiled, past its expiry date, or of unknown origin',
];

export default function HowItWorksPage() {
  const { user } = useAuth();
  return (
    <div>
      <Navbar />

      {/* Hero */}
      <section style={{ background: 'linear-gradient(165deg, #f0fdf4 0%, #d8f3dc 100%)', padding: '64px 32px', textAlign: 'center' }}>
        <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8, background: 'rgba(45,106,79,0.1)', border: '1px solid rgba(45,106,79,0.2)', borderRadius: 999, padding: '6px 16px', marginBottom: 20, fontSize: '0.82rem', fontWeight: 700, color: 'var(--brand-green)' }}>
          <Leaf size={14} /> How Annadaan! Works
        </div>
        <h1 style={{ fontFamily: "'Plus Jakarta Sans', sans-serif", fontWeight: 900, fontSize: 'clamp(2rem, 5vw, 3rem)', marginBottom: 16 }}>
          Simple. Transparent. <span style={{ color: 'var(--brand-green)' }}>Impactful.</span>
        </h1>
        <p style={{ color: 'var(--brand-text-soft)', fontSize: '1.05rem', maxWidth: 540, margin: '0 auto' }}>
          Follow the journey of a meal from your kitchen to someone who needs it most.
        </p>
      </section>

      {/* Steps */}
      <section style={{ padding: '64px 32px', maxWidth: 880, margin: '0 auto' }}>
        <h2 style={{ textAlign: 'center', fontFamily: "'Plus Jakarta Sans', sans-serif", fontWeight: 800, fontSize: '1.6rem', marginBottom: 48 }}>
          Step-by-Step Process
        </h2>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
          {STEPS.map((s, i) => (
            <div key={i} style={{ display: 'flex', gap: 24, alignItems: 'flex-start', background: 'white', borderRadius: 16, padding: 24, border: '1px solid var(--brand-border)', boxShadow: '0 2px 10px rgba(0,0,0,0.04)' }}>
              <div style={{ width: 52, height: 52, borderRadius: '50%', background: 'var(--brand-green)', color: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '1.5rem', flexShrink: 0 }}>{s.emoji}</div>
              <div>
                <div style={{ fontWeight: 800, fontSize: '1rem', fontFamily: "'Plus Jakarta Sans', sans-serif", marginBottom: 6 }}>
                  Step {s.n}: {s.title}
                </div>
                <p style={{ color: 'var(--brand-text-soft)', fontSize: '0.9rem', lineHeight: 1.7, margin: 0 }}>{s.body}</p>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* Food Safety */}
      <section style={{ background: '#fffbeb', borderTop: '1px solid #fcd34d', borderBottom: '1px solid #fcd34d', padding: '56px 32px' }}>
        <div style={{ maxWidth: 760, margin: '0 auto' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 28 }}>
            <AlertTriangle size={24} style={{ color: '#92400e' }} />
            <h2 style={{ fontFamily: "'Plus Jakarta Sans', sans-serif", fontWeight: 800, fontSize: '1.4rem', color: '#92400e', margin: 0 }}>
              Food Safety Guidelines
            </h2>
          </div>
          <p style={{ color: '#78350f', marginBottom: 20, lineHeight: 1.7 }}>
            Annadaan! does not independently verify food quality. Donors are fully responsible for ensuring that donated food is safe, fresh, and handled correctly. By submitting a donation, donors confirm the following:
          </p>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {SAFETY.map((s, i) => (
              <div key={i} style={{ display: 'flex', alignItems: 'flex-start', gap: 10, background: '#fffde7', borderRadius: 10, padding: '10px 14px', border: '1px solid #fde68a' }}>
                <CheckCircle size={16} style={{ color: '#d97706', flexShrink: 0, marginTop: 2 }} />
                <span style={{ fontSize: '0.875rem', color: '#78350f' }}>{s}</span>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* CTA */}
      <section style={{ padding: '56px 32px', textAlign: 'center', background: 'var(--brand-green)' }}>
        <h2 style={{ fontFamily: "'Plus Jakarta Sans', sans-serif", fontWeight: 800, color: 'white', fontSize: '1.8rem', marginBottom: 14 }}>
          Ready to make a difference?
        </h2>
        <p style={{ color: 'rgba(255,255,255,0.8)', marginBottom: 28 }}>Join donors and NGOs turning surplus food into shared hope.</p>
        <div style={{ display: 'flex', gap: 12, justifyContent: 'center', flexWrap: 'wrap' }}>
          <Link to={user ? '/donor/dashboard' : '/register'} className="btn" style={{ background: 'white', color: 'var(--brand-green)', fontWeight: 700 }}>
            🍛 Donate Food
          </Link>
          <Link to={user ? '/ngo/dashboard' : '/register'} className="btn" style={{ background: 'transparent', color: 'white', border: '2px solid rgba(255,255,255,0.5)' }}>
            🏥 Register as NGO
          </Link>
        </div>
      </section>
    </div>
  );
}
