import api from './api';

export const applicationService = {
  apply: (jobId) => api.post('/student/applications', { jobId }).then((r) => r.data),
  mine: () => api.get('/student/applications').then((r) => r.data),
};
