import axios from 'axios';

const TOKEN_KEY = 'careersphere_token';

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (t) => localStorage.setItem(TOKEN_KEY, t),
  clear: () => localStorage.removeItem(TOKEN_KEY),
};

/** One shared Axios instance. Every service file uses this - components never call axios directly. */
const api = axios.create({
  // Same address as the web app: Vite passes /api to the backend in development,
  // and the deployed image serves both. Set VITE_API_BASE_URL only to call a backend elsewhere.
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 20000,
});

// Attach the JWT to every request.
api.interceptors.request.use((config) => {
  const token = tokenStore.get();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// If the token expired or is invalid, log out and go to the login page.
api.interceptors.response.use(
  (res) => res,
  (error) => {
    const isAuthCall = error.config?.url?.startsWith('/auth/');
    if (error.response?.status === 401 && !isAuthCall) {
      tokenStore.clear();
      if (window.location.pathname !== '/login') window.location.assign('/login?expired=1');
    }
    return Promise.reject(error);
  }
);

/** Turns any Axios error into a sentence a user can understand. */
export function errorMessage(error) {
  if (!error?.response) {
    return error?.code === 'ECONNABORTED'
      ? 'The server took too long to respond. Please try again.'
      : 'Cannot reach the server. Is the backend running on port 8080?';
  }
  const data = error.response.data;
  if (data && typeof data === 'object' && data.message) return data.message;
  switch (error.response.status) {
    case 400: return 'Some of the information is invalid.';
    case 401: return 'Please log in again.';
    case 403: return 'You do not have permission to do this.';
    case 404: return 'Not found.';
    case 413: return 'File is too large. The maximum size is 5 MB.';
    default: return 'Something went wrong on the server. Please try again.';
  }
}

/** Field-level validation errors from the backend, e.g. { cgpa: "CGPA must be between 0 and 10" }. */
export function fieldErrors(error) {
  return error?.response?.data?.fields || {};
}

/** Opens a protected file (e.g. a resume) in a new tab. A plain link would not send the JWT. */
export async function openProtectedFile(url) {
  // Inside the Android app, WebView can't show a PDF, so the file is handed to the phone's own viewer.
  const android = window.CareerSphereAndroid;
  const tab = android ? null : window.open('', '_blank');
  try {
    const res = await api.get(url, { responseType: 'blob' });
    if (android) {
      const name = /filename="?([^";]+)"?/i.exec(res.headers['content-disposition'] || '')?.[1] || 'resume.pdf';
      const base64 = await new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result).split(',')[1]);
        reader.onerror = reject;
        reader.readAsDataURL(res.data);
      });
      android.openFile(base64, res.data.type || 'application/pdf', name);
      return;
    }
    const blobUrl = URL.createObjectURL(res.data);
    if (tab) tab.location.href = blobUrl;
    else window.location.href = blobUrl;
    setTimeout(() => URL.revokeObjectURL(blobUrl), 60000);
  } catch (e) {
    if (tab) tab.close();
    if (e.response?.data instanceof Blob) {
      try { e.response.data = JSON.parse(await e.response.data.text()); } catch { /* not JSON */ }
    }
    throw e;
  }
}

export default api;
