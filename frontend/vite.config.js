import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The web app calls /api on its own address, and Vite passes those calls on to the backend.
// That way the same web app works in a browser, in the Xcode app, in the Android emulator
// (which reaches this Mac at 10.0.2.2) and on a phone on the same Wi-Fi.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    host: true, // also listen on this Mac's network address, for the Android emulator and phones
    proxy: { '/api': 'http://localhost:8080' },
  },
});
