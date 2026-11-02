import { expect, test as base } from '@playwright/test';

// The reset route is not proxied by Vite and not part of the API contract, so it is called
// directly on the (loopback-only) test backend.
const RESET_URL = 'http://127.0.0.1:8080/__test__/reset';

// Test routes refuse requests without this header: a web page cannot add it to a cross-origin "simple" POST.
export const TEST_HEADERS = { 'X-Calendar-Test': '1' };

export const test = base.extend<{ resetState: void }>({
  resetState: [
    async ({ request }, use) => {
      const response = await request.post(RESET_URL, { headers: TEST_HEADERS });
      expect(response.status(), 'POST /__test__/reset: is the backend running with CALENDAR_TEST_MODE=true?').toBe(204);
      await use();
    },
    { auto: true },
  ],
});

export { expect };
export type { Page } from '@playwright/test';
