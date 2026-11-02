import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    // Fixed guest time zone so grouping by the guest's calendar date is deterministic.
    env: { TZ: 'Asia/Tbilisi' },
    include: ['src/**/*.test.{ts,tsx}'],
  },
});
