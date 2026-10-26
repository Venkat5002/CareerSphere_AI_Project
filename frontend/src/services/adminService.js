import api, { openProtectedFile } from './api';

export const adminService = {
  stats: () => api.get('/admin/stats').then((r) => r.data),
  users: () => api.get('/admin/users').then((r) => r.data),
  /** goal: 'REACH' (first job for most students) or 'MATCHES' (most new matches). */
  /** status: PENDING, VERIFIED, REJECTED or ALL */
  certificates: (status) => api.get('/admin/certificates', { params: { status } }).then((r) => r.data),
  reviewCertificate: (id, status, note) => api.patch(`/admin/certificates/${id}`, { status, note }).then((r) => r.data),
  openCertificate: (id) => openProtectedFile(`/admin/certificates/${id}/file`),
  leaderboard: () => api.get('/admin/leaderboard').then((r) => r.data),
  workshops: (rounds, goal) => api.get('/admin/workshops', { params: { rounds, goal } }).then((r) => r.data),
};
