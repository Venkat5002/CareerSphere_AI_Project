import api from './api';

export const studentService = {
  dashboard: () => api.get('/student/dashboard').then((r) => r.data),
  getProfile: () => api.get('/student/profile').then((r) => r.data),
  updateProfile: (profile) => api.put('/student/profile', profile).then((r) => r.data),
  opportunities: () => api.get('/student/opportunities').then((r) => r.data),
  roadmap: () => api.get('/student/roadmap').then((r) => r.data),
  invitations: () => api.get('/student/invitations').then((r) => r.data),
  whatIf: (skills) =>
    api.get('/student/what-if', { params: { skills: skills.join(',') } }).then((r) => r.data),
};
