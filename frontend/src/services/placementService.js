import api, { openProtectedFile } from './api';

/** Placement profile: readiness, consent, introduction video, skill assessments, certificates, leaderboard. */
export const placementService = {
  readiness: () => api.get('/student/readiness').then((r) => r.data),
  setConsent: (consent) => api.put('/student/consent', { consent }).then((r) => r.data),
  leaderboard: () => api.get('/student/leaderboard').then((r) => r.data),

  video: () => api.get('/student/video').then((r) => (r.status === 204 ? null : r.data)),
  uploadVideo: (blob, fileName) => {
    const form = new FormData();
    form.append('video', blob, fileName);
    return api.post('/student/video', form, { timeout: 120000 }).then((r) => r.data);
  },
  deleteVideo: () => api.delete('/student/video'),
  videoUrl: () => fetchBlobUrl('/student/video/file'),

  assessments: () => api.get('/student/assessments').then((r) => r.data),
  startAssessment: (skill) => api.post('/student/assessments/start', null, { params: { skill } }).then((r) => r.data),
  submitAssessment: (attemptId, answers) =>
    api.post(`/student/assessments/${attemptId}/submit`, { answers }).then((r) => r.data),

  certificates: () => api.get('/student/certificates').then((r) => r.data),
  addCertificate: (fields, file) => {
    const form = new FormData();
    Object.entries(fields).forEach(([k, v]) => { if (v !== '' && v != null) form.append(k, v); });
    form.append('file', file);
    return api.post('/student/certificates', form).then((r) => r.data);
  },
  deleteCertificate: (id) => api.delete(`/student/certificates/${id}`),
  openCertificate: (id) => openProtectedFile(`/student/certificates/${id}/file`),
};

/** Downloads a protected file (the JWT must be sent) and returns an object URL for <video> or <img>. */
export async function fetchBlobUrl(url) {
  const res = await api.get(url, { responseType: 'blob', timeout: 120000 });
  return URL.createObjectURL(res.data);
}
