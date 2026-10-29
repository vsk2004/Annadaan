import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Navbar from '../components/Navbar';
import { Leaf, Heart, Users, CheckCircle, ArrowRight, MapPin, Clock } from 'lucide-react';

const HOW_STEPS = [
  { emoji: '📋', title: 'Register', desc: 'Sign up as a donor or verified NGO in minutes.' },
  { emoji: '🍛', title: 'Donate or Discover', desc: 'Donors post surplus food. NGOs find nearby donations on their dashboard.' },
  { emoji: '🚚', title: 'Pickup & Deliver', desc: 'NGO accepts, picks up, and delivers. Every step is tracked.' },
];

const FEATURES = [
  { icon: MapPin, title: 'GPS-Based Matching', desc: 'Haversine formula finds donations within your exact 5 km or 10 km radius. No city-name guessing.' },
  { icon: Clock, title: 'Real-Time Status', desc: 'Track every donation from AVAILABLE → ACCEPTED → PICKED UP → DELIVERED with timestamps.' },
  { icon: CheckCircle, title: 'Food Safety Checklist', desc: 'Donors confirm safety before posting. Every donation has a food safety confirmation on file.' },
];

export default function LandingPage() {
  const { user } = useAuth();
  const navigate = useNavigate();

  const donatePath = !user        ? '/register'
                   : user.role === 'DONOR' ? '/donor/dashboard'
                   : '/ngo/dashboard';
  const findPath   = !user        ? '/register'
                   : user.role === 'NGO'   ? '/ngo/dashboard'
                   : '/donor/dashboard';

  return (
    <div>
      <Navbar />

      {/* Hero */}
      <section className="hero-section">
        <div style={{ position: 'relative', zIndex: 1, maxWidth: 760, width: '100%' }}>
          <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8, background: 'rgba(45,106,79,0.12)', border: '1px solid rgba(45,106,79,0.3)', borderRadius: 999, padding: '7px 18px', marginBottom: 24, fontSize: '0.85rem', fontWeight: 700, color: 'var(--brand-green)' }}>
            <Leaf size={15} /> Good Food. Shared Hope.
          </div>
          <h1 className="hero-title">
            Share Food.<br />
            <span className="accent">Spread Hope.</span>
          </h1>
          <p className="hero-subtitle">
            Rescue surplus food from homes, hotels, and events. Connect it directly with verified NGOs who can put it to use — before it goes to waste.
          </p>
          <div style={{ display: 'flex', gap: 14, justifyContent: 'center', flexWrap: 'wrap' }}>
            <Link to={donatePath} className="btn btn-primary btn-xl" style={{ minWidth: 180 }}>
              🍛 Donate Food <ArrowRight size={16} />
            </Link>
            <Link to={findPath} className="btn btn-secondary btn-xl" style={{ minWidth: 180 }}>
              🏥 Find Food
            </Link>
          </div>
          {!user && (
            <p style={{ marginTop: 18, fontSize: '0.85rem', color: 'var(--brand-text-muted)' }}>
              Already registered? <Link to="/login" style={{ fontWeight: 700 }}>Sign in →</Link>
            </p>
          )}
        </div>
      </section>

      {/* How It Works */}
      <section style={{ padding: '72px 32px', background: 'white' }}>
        <div style={{ maxWidth: 880, margin: '0 auto' }}>
          <div style={{ textAlign: 'center', marginBottom: 48 }}>
            <h2 style={{ fontFamily: "'Plus Jakarta Sans', sans-serif", fontWeight: 800, fontSize: '1.8rem', marginBottom: 10 }}>How Annadaan! Works</h2>
            <p style={{ color: 'var(--brand-text-muted)', fontSize: '1rem' }}>Three steps to turn surplus into impact</p>
          </div>
          <div className="grid-3">
            {HOW_STEPS.map((s, i) => (
              <div key={i} className="step-card">
                <div className="step-number">{i + 1}</div>
                <div style={{ fontSize: '2rem', marginBottom: 12 }}>{s.emoji}</div>
                <h3 style={{ fontFamily: "'Plus Jakarta Sans', sans-serif", fontWeight: 800, fontSize: '1rem', marginBottom: 8 }}>{s.title}</h3>
                <p style={{ color: 'var(--brand-text-muted)', fontSize: '0.875rem', lineHeight: 1.6 }}>{s.desc}</p>
              </div>
            ))}
          </div>
          <div style={{ textAlign: 'center', marginTop: 32 }}>
            <Link to="/how-it-works" style={{ fontWeight: 700, color: 'var(--brand-green)', fontSize: '0.9rem' }}>
              Read the full guide →
            </Link>
          </div>
        </div>
      </section>

      {/* Features */}
      <section style={{ padding: '72px 32px', background: 'var(--brand-cream)' }}>
        <div style={{ maxWidth: 880, margin: '0 auto' }}>
          <div style={{ textAlign: 'center', marginBottom: 48 }}>
            <h2 style={{ fontFamily: "'Plus Jakarta Sans', sans-serif", fontWeight: 800, fontSize: '1.8rem', marginBottom: 10 }}>Built for Trust</h2>
            <p style={{ color: 'var(--brand-text-muted)' }}>Every feature designed for reliability, safety, and transparency</p>
          </div>
          <div className="grid-2 feature-grid">
            {FEATURES.map((f, i) => {
              const Icon = f.icon;
              return (
                <div key={i} className="feature-card">
                  <div style={{ width: 44, height: 44, borderRadius: 12, background: 'rgba(45,106,79,0.1)', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 14 }}>
                    <Icon size={20} style={{ color: 'var(--brand-green)' }} />
                  </div>
                  <h3 style={{ fontFamily: "'Plus Jakarta Sans', sans-serif", fontWeight: 700, fontSize: '0.95rem', marginBottom: 8 }}>{f.title}</h3>
                  <p style={{ color: 'var(--brand-text-muted)', fontSize: '0.875rem', lineHeight: 1.65 }}>{f.desc}</p>
                </div>
              );
            })}
          </div>
        </div>
      </section>

      {/* Food Safety Note */}
      <section style={{ padding: '48px 32px', background: '#fffbeb', borderTop: '1px solid #fde68a' }}>
        <div style={{ maxWidth: 680, margin: '0 auto', textAlign: 'center' }}>
          <div style={{ fontSize: '2rem', marginBottom: 12 }}>⚠️</div>
          <h3 style={{ fontFamily: "'Plus Jakarta Sans', sans-serif", fontWeight: 700, fontSize: '1.1rem', color: '#92400e', marginBottom: 10 }}>
            Food Safety Responsibility
          </h3>
          <p style={{ color: '#78350f', fontSize: '0.875rem', lineHeight: 1.7 }}>
            Annadaan! does not independently verify food quality. Donors must confirm on a safety checklist that food is properly prepared, stored, and handled. NGOs collect food at their discretion. <Link to="/how-it-works" style={{ color: '#92400e', fontWeight: 700 }}>Read our safety guidelines →</Link>
          </p>
        </div>
      </section>

      {/* CTA */}
      <section style={{ padding: '72px 32px', background: 'var(--brand-green)', textAlign: 'center' }}>
        <div style={{ fontSize: '2.5rem', marginBottom: 12 }}>🌿</div>
        <h2 style={{ fontFamily: "'Plus Jakarta Sans', sans-serif", fontWeight: 900, color: 'white', fontSize: '2rem', marginBottom: 12 }}>
          Start Your Food Rescue Journey
        </h2>
        <p style={{ color: 'rgba(255,255,255,0.8)', marginBottom: 28, fontSize: '1rem' }}>
          Join donors and NGOs across India turning surplus food into shared hope.
        </p>
        <div style={{ display: 'flex', gap: 14, justifyContent: 'center', flexWrap: 'wrap' }}>
          <Link to="/register" className="btn btn-xl" style={{ background: 'white', color: 'var(--brand-green)', fontWeight: 800 }}>
            Get Started Free
          </Link>
          <Link to="/how-it-works" className="btn btn-xl" style={{ background: 'transparent', border: '2px solid rgba(255,255,255,0.5)', color: 'white' }}>
            Learn More
          </Link>
        </div>
      </section>

      {/* Footer */}
      <footer className="footer">
        <div style={{ maxWidth: 880, margin: '0 auto' }}>
          <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 32, marginBottom: 32 }}>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: 'white', fontFamily: "'Plus Jakarta Sans', sans-serif", fontWeight: 900, fontSize: '1.2rem', marginBottom: 10 }}>
                <Leaf size={20} /> Annadaan!
              </div>
              <p style={{ fontSize: '0.85rem', opacity: 0.75, lineHeight: 1.7 }}>
                Good Food. Shared Hope.<br />
                Connecting donors with verified NGOs to rescue surplus food.
              </p>
            </div>
            <div>
              <div style={{ fontWeight: 700, marginBottom: 10, color: 'white', fontSize: '0.85rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Platform</div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                <Link to="/" style={{ fontSize: '0.85rem' }}>Home</Link>
                <Link to="/how-it-works" style={{ fontSize: '0.85rem' }}>How It Works</Link>
                <Link to="/register" style={{ fontSize: '0.85rem' }}>Register</Link>
              </div>
            </div>
          </div>
          <div style={{ borderTop: '1px solid rgba(255,255,255,0.15)', paddingTop: 16, fontSize: '0.78rem', opacity: 0.65, display: 'flex', justifyContent: 'space-between' }}>
            <span>© 2024 Annadaan! — Good Food. Shared Hope.</span>
            <span>Built with ❤️ to fight food waste</span>
          </div>
        </div>
      </footer>
    </div>
  );
}
