import api, { openProtectedFile } from './api';
import { fetchBlobUrl } from './placementService';

export const recruiterService = {
  dashboard: () => api.get('/recruiter/dashboard').then((r) => r.data),
  getProfile: () => api.get('/recruiter/profile').then((r) => r.data),
  updateProfile: (p) => api.put('/recruiter/profile', p).then((r) => r.data),

  jobs: () => api.get('/recruiter/jobs').then((r) => r.data),
  createJob: (job) => api.post('/recruiter/jobs', job).then((r) => r.data),
  updateJob: (id, job) => api.put(`/recruiter/jobs/${id}`, job).then((r) => r.data),
  setJobStatus: (id, status) => api.patch(`/recruiter/jobs/${id}/status`, { status }).then((r) => r.data),
  deleteJob: (id) => api.delete(`/recruiter/jobs/${id}`),

  talent: (jobId) => api.get(`/recruiter/jobs/${jobId}/talent`).then((r) => r.data),
  invite: (jobId, studentId, message) => api.post(`/recruiter/jobs/${jobId}/invite/${studentId}`, { message }).then((r) => r.data),
  applicants: (jobId) => api.get(`/recruiter/jobs/${jobId}/applicants`).then((r) => r.data),
  updateStatus: (applicationId, status, note) =>
    api.patch(`/recruiter/applications/${applicationId}/status`, { status, note }).then((r) => r.data),
  openResume: (applicationId) => openProtectedFile(`/recruiter/applications/${applicationId}/resume`),
  videoUrl: (applicationId) => fetchBlobUrl(`/recruiter/applications/${applicationId}/video`),
};
