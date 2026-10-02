import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// The browser talks only to the Vite server, which forwards /api to the Spring Boot application.
// That keeps everything on one origin, so no CORS setup is needed.
const proxy = {
  '/api': { target: 'http://localhost:8080', changeOrigin: false },
};

export default defineConfig({
  plugins: [react()],
  server: { port: 5173, proxy },
  // `npm run preview` serves the production build; it needs the same forwarding.
  preview: { port: 4173, proxy },
  test: {
    environment: 'node',
    include: ['src/**/*.test.{ts,tsx}'],
  },
});
