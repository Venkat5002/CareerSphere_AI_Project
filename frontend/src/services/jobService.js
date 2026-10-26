import api from './api';

/** Jobs as seen by a student (with match scores). */
export const jobService = {
  list: ({ q, sort } = {}) => api.get('/student/jobs', { params: { q, sort } }).then((r) => r.data),
  get: (id) => api.get(`/student/jobs/${id}`).then((r) => r.data),
};
