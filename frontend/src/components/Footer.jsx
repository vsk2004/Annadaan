import { Link } from 'react-router-dom';
import { Heart, Mail, Phone, MapPin, Twitter, Instagram, Facebook, Github } from 'lucide-react';

export default function Footer() {
  return (
    <footer className="footer">
      <div className="container">
        <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr 1fr 1fr', gap: 48, marginBottom: 48 }}>
          {/* Brand */}
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 16 }}>
              <div style={{ width: 36, height: 36, background: 'var(--gradient-brand)', borderRadius: 10, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 18 }}>🍽️</div>
              <span style={{ fontFamily: "'Plus Jakarta Sans', sans-serif", fontWeight: 800, fontSize: '1.2rem' }}>FoodBridge</span>
            </div>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.875rem', lineHeight: 1.7, maxWidth: 280, marginBottom: 20 }}>
              Connecting generous donors with verified NGOs to reduce food waste and fight hunger in our communities.
            </p>
            <div style={{ display: 'flex', gap: 12 }}>
              {[Twitter, Instagram, Facebook, Github].map((Icon, i) => (
                <a key={i} href="#" style={{ width: 36, height: 36, borderRadius: 8, background: 'var(--bg-glass-light)', border: '1px solid var(--border)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--text-muted)', transition: 'var(--transition)' }}>
                  <Icon size={16} />
                </a>
              ))}
            </div>
          </div>

          {/* Platform */}
          <div>
            <h4 style={{ fontSize: '0.875rem', fontWeight: 700, marginBottom: 16, color: 'var(--text-primary)' }}>Platform</h4>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {['How It Works', 'Donate Food', 'Find Food', 'Our Impact', 'Food Safety'].map(link => (
                <a key={link} href="#" style={{ fontSize: '0.875rem', color: 'var(--text-muted)', transition: 'var(--transition)' }}
                   onMouseEnter={e => e.target.style.color = 'var(--green-400)'}
                   onMouseLeave={e => e.target.style.color = 'var(--text-muted)'}>{link}</a>
              ))}
            </div>
          </div>

          {/* Organization */}
          <div>
            <h4 style={{ fontSize: '0.875rem', fontWeight: 700, marginBottom: 16, color: 'var(--text-primary)' }}>Organization</h4>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {['About Us', 'Our Team', 'Blog', 'Careers', 'Press'].map(link => (
                <a key={link} href="#" style={{ fontSize: '0.875rem', color: 'var(--text-muted)', transition: 'var(--transition)' }}
                   onMouseEnter={e => e.target.style.color = 'var(--green-400)'}
                   onMouseLeave={e => e.target.style.color = 'var(--text-muted)'}>{link}</a>
              ))}
            </div>
          </div>

          {/* Contact */}
          <div>
            <h4 style={{ fontSize: '0.875rem', fontWeight: 700, marginBottom: 16, color: 'var(--text-primary)' }}>Contact</h4>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <a href="mailto:hello@foodbridge.org" style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: '0.875rem', color: 'var(--text-muted)' }}>
                <Mail size={14} /> hello@foodbridge.org
              </a>
              <a href="tel:+911234567890" style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: '0.875rem', color: 'var(--text-muted)' }}>
                <Phone size={14} /> +91 12345 67890
              </a>
              <span style={{ display: 'flex', alignItems: 'flex-start', gap: 8, fontSize: '0.875rem', color: 'var(--text-muted)' }}>
                <MapPin size={14} style={{ marginTop: 2, flexShrink: 0 }} /> 123 Food Street, Mumbai, Maharashtra 400001
              </span>
            </div>
          </div>
        </div>

        <div style={{ borderTop: '1px solid var(--border)', paddingTop: 24, display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 16 }}>
          <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
            © 2026 FoodBridge. Made with <Heart size={12} style={{ display: 'inline', color: '#f87171' }} /> to fight hunger.
          </p>
          <div style={{ display: 'flex', gap: 20 }}>
            {['Privacy Policy', 'Terms of Service', 'Cookie Policy'].map(link => (
              <a key={link} href="#" style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{link}</a>
            ))}
          </div>
        </div>
      </div>
    </footer>
  );
}
