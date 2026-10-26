import api from './api';

export const copilotService = {
  status: () => api.get('/student/copilot/status').then((r) => r.data),
  /** messages: [{ role: 'user' | 'assistant', text }], newest last. */
  ask: (messages) => api.post('/student/copilot', { messages }, { timeout: 90000 }).then((r) => r.data),
};
