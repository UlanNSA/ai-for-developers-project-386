import { afterEach, beforeAll, expect, test, vi } from 'vitest';
import { getHealth } from './generated';
import { client } from './generated/client.gen';

beforeAll(() => {
  // Node's Request requires an absolute URL; browsers resolve /api against the page origin.
  client.setConfig({ baseUrl: 'http://localhost' });
});

afterEach(() => {
  vi.unstubAllGlobals();
});

// Guards the TypeSpec -> OpenAPI -> generated SDK chain: if the contract changes,
// regenerating the SDK breaks this call at type-check or runtime.
test('generated SDK calls GET /api/health and returns the typed body', async () => {
  const fetchMock = vi.fn(async () => Response.json({ status: 'ok' }));
  vi.stubGlobal('fetch', fetchMock);

  const { data } = await getHealth();

  expect(data?.status).toBe('ok');
  const request = (fetchMock.mock.calls[0] as unknown as [Request])[0];
  expect(new URL(request.url).pathname).toBe('/api/health');
  expect(request.method).toBe('GET');
});
