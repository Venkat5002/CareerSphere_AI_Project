import api, { openProtectedFile } from './api';

export const ALLOWED_EXTENSIONS = ['pdf', 'doc', 'docx'];
export const MAX_RESUME_BYTES = 5 * 1024 * 1024;

/** Checks the file in the browser before sending it. Returns an error message or null. */
export function validateResume(file) {
  if (!file) return 'Please choose a file.';
  const ext = file.name.split('.').pop().toLowerCase();
  if (!ALLOWED_EXTENSIONS.includes(ext)) return 'Only PDF, DOC or DOCX files are allowed.';
  if (file.size > MAX_RESUME_BYTES) return 'File is too large. The maximum size is 5 MB.';
  if (file.size === 0) return 'This file is empty.';
  return null;
}

export const resumeService = {
  /** Returns resume details, or null if the student has not uploaded one (HTTP 204). */
  get: () => api.get('/student/resume').then((r) => (r.status === 204 ? null : r.data)),

  /**
   * Sends the file as multipart/form-data in a field named "resume".
   * Important: do NOT set the Content-Type header yourself - the browser must add
   * the multipart boundary, otherwise Spring cannot read the file.
   */
  upload: (file, onProgress) => {
    const form = new FormData();
    form.append('resume', file);
    return api
      .post('/student/resume', form, {
        onUploadProgress: (e) => {
          if (onProgress && e.total) onProgress(Math.round((e.loaded * 100) / e.total));
        },
      })
      .then((r) => r.data);
  },

  remove: () => api.delete('/student/resume'),
  open: () => openProtectedFile('/student/resume/file'),

  /** Reads the uploaded resume and returns suggested skills with evidence. Changes nothing. */
  findSkills: () => api.post('/student/resume/skills', null, { timeout: 90000 }).then((r) => r.data),
  /** Adds the skills the student confirmed to their profile. */
  addSkills: (skills) => api.post('/student/profile/skills', { skills }).then((r) => r.data),
};
