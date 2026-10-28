import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeAll, expect, test, vi } from 'vitest';
import { App } from './App';
import { client } from './api/generated/client.gen';

beforeAll(() => {
  // Node's Request requires an absolute URL; browsers resolve /api against the page origin.
  client.setConfig({ baseUrl: 'http://localhost' });
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

test('shows backend status returned by the generated SDK', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => Response.json({ status: 'ok' })),
  );

  render(<App />);

  expect(await screen.findByText('Статус backend: ok')).toBeTruthy();
});
