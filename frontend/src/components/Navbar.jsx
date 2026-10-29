import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Leaf, Menu, X, LogOut, User } from 'lucide-react';
import { useState } from 'react';

export default function Navbar() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);

  const handleLogout = () => { logout(); navigate('/'); };

  const dashboardPath = user?.role === 'DONOR' ? '/donor/dashboard'
                      : user?.role === 'NGO'   ? '/ngo/dashboard'
                      : null;

  const isDash = location.pathname.includes('/donor/') || location.pathname.includes('/ngo/');
  if (isDash) return null; // dashboards have their own sidebar nav

  return (
    <nav className="navbar">
      {/* Brand */}
      <Link to="/" className="navbar-brand">
        <Leaf size={22} style={{ color: 'var(--brand-green)' }} />
        Annadaan!
      </Link>

      {/* Desktop links */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
        <Link to="/"           className={`nav-link ${location.pathname === '/' ? 'active' : ''}`}>Home</Link>
        <Link to="/how-it-works" className={`nav-link ${location.pathname === '/how-it-works' ? 'active' : ''}`}>How It Works</Link>
        {!user ? (
          <>
            <Link to="/login"    className="nav-link">Login</Link>
            <Link to="/register" className="btn btn-primary btn-sm" style={{ marginLeft: 8 }}>Register</Link>
          </>
        ) : (
          <>
            {dashboardPath && (
              <Link to={dashboardPath} className="nav-link">Dashboard</Link>
            )}
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginLeft: 12,
                          padding: '6px 12px', borderRadius: 999,
                          background: 'rgba(45,106,79,0.08)', border: '1px solid rgba(45,106,79,0.15)' }}>
              <User size={14} style={{ color: 'var(--brand-green)' }} />
              <span style={{ fontSize: '0.82rem', fontWeight: 600, color: 'var(--brand-green)' }}>
                {user.name?.split(' ')[0]}
              </span>
            </div>
            <button className="btn btn-secondary btn-sm" onClick={handleLogout} style={{ marginLeft: 8 }}>
              <LogOut size={14} /> Logout
            </button>
          </>
        )}
      </div>
    </nav>
  );
}
