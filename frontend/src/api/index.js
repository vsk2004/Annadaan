import axios from 'axios';

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

const api = axios.create({ baseURL: API_BASE });

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (res) => res,
  (error) => {
    const isAuthRequest = error.config?.url?.includes('/auth/login') || error.config?.url?.includes('/auth/register');

    if (error.response?.status === 401 && !isAuthRequest) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

// ── Auth ──────────────────────────────────────────────────────
export const authAPI = {
  register: (data) => api.post('/auth/register', data),
  login: (data) => api.post('/auth/login', data),
  forgotPassword: (data) => api.post('/auth/forgot-password', data),
  resetPassword: (data) => api.post('/auth/reset-password', data),
  me: () => api.get('/auth/me'),
};

// ── Donations (unified — all roles use this) ──────────────────
export const donationAPI = {
  create: (formData) => api.post('/donations', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }),
  getById: (id) => api.get(`/donations/${id}`),
  mine: () => api.get('/donations/mine'),
  nearby: (radius = 5) => api.get(`/donations/nearby?radiusKm=${radius}`),
  accept: (id, radius = 5) => api.post(`/donations/${id}/accept?radiusKm=${radius}`),
  pickup: (id, note = '') => api.post(`/donations/${id}/pickup`, { note }),
  deliver: (id, data = {}) => api.post(`/donations/${id}/deliver`, data),
  cancel: (id, reason = '') => api.post(`/donations/${id}/cancel`, { reason }),
  history: (id) => api.get(`/donations/${id}/history`),
  retryEmail: (id) => api.post(`/donations/${id}/retry-email`),
};

// ── NGO ───────────────────────────────────────────────────────
export const ngoAPI = {
  getProfile: () => api.get('/ngo/profile'),
  updateProfile: (data) => api.put('/ngo/profile', data),
  getStats: () => api.get('/ngo/stats'),
  activeDonations: () => api.get('/ngo/active-donations'),
  history: () => api.get('/ngo/history'),
  notifications: () => api.get('/ngo/notifications'),
  getNotificationPreferences: () => api.get('/ngo/notification-preferences'),
  updateNotificationPreferences: (data) => api.put('/ngo/notification-preferences', data),
  smsNotifications: () => api.get('/ngo/sms-notifications'),
  emailNotifications: () => api.get('/ngo/email-notifications'),
};

export default api;
