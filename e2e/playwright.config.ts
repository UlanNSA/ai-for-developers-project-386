import { defineConfig, devices } from '@playwright/test';

const isCI = Boolean(process.env.CI);

// Frozen "now" of the test backend: Monday 2026-11-02 11:00 in the Owner's zone (Asia/Almaty, UTC+5).
const FIXED_NOW = '2026-11-02T06:00:00Z';
const BACKEND_PORT = 8080; // the Vite dev server proxies /api to this port (frontend/vite.config.ts)
const FRONTEND_PORT = 5173;

export default defineConfig({
  testDir: './tests',
  // One backend with shared in-memory state: tests must not run in parallel.
  workers: 1,
  fullyParallel: false,
  forbidOnly: isCI,
  retries: isCI ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: `http://localhost:${FRONTEND_PORT}`,
    // Fixed guest time zone and locale, so rendered dates and times are deterministic.
    timezoneId: 'Asia/Tbilisi',
    locale: 'ru-RU',
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        // Optional local fallback: PW_CHANNEL=msedge or chrome uses an installed browser. CI uses bundled Chromium.
        ...(process.env.PW_CHANNEL ? { channel: process.env.PW_CHANNEL } : {}),
      },
    },
  ],
  webServer: [
    {
      // The real Ktor backend in test mode (fixed clock, reset route, loopback only).
      command: 'java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain run',
      cwd: '../backend',
      env: {
        CALENDAR_TEST_MODE: 'true',
        CALENDAR_FIXED_NOW: FIXED_NOW,
        PORT: String(BACKEND_PORT),
      },
      url: `http://127.0.0.1:${BACKEND_PORT}/api/health`,
      reuseExistingServer: !isCI,
      timeout: 240_000,
      gracefulShutdown: { signal: 'SIGTERM', timeout: 10_000 },
    },
    {
      command: `npm run dev -- --port ${FRONTEND_PORT} --strictPort`,
      cwd: '../frontend',
      url: `http://localhost:${FRONTEND_PORT}`,
      reuseExistingServer: !isCI,
      timeout: 60_000,
    },
  ],
});
