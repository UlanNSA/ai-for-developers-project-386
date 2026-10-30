import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeAll, expect, test, vi } from 'vitest';
import { client } from './api/generated/client.gen';
import { HomePage } from './HomePage';

beforeAll(() => {
  // Node's Request requires an absolute URL; browsers resolve /api against the page origin.
  client.setConfig({ baseUrl: 'http://localhost' });
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

function renderHome() {
  render(
    <MemoryRouter>
      <HomePage />
    </MemoryRouter>,
  );
}

test('shows a loading state while the owner is being fetched', () => {
  vi.stubGlobal('fetch', () => new Promise<Response>(() => {}));

  renderHome();

  expect(screen.getByText('Загрузка…')).toBeTruthy();
});

test('shows owner name and bio from GET /api/owner', async () => {
  const fetchMock = vi.fn(async () =>
    Response.json({ name: 'Анна Петрова', bio: 'Консультирую по продукту', timeZone: 'Asia/Almaty' }),
  );
  vi.stubGlobal('fetch', fetchMock);

  renderHome();

  expect(await screen.findByRole('heading', { level: 2, name: 'Анна Петрова' })).toBeTruthy();
  expect(screen.getByText('Консультирую по продукту')).toBeTruthy();
  expect(screen.queryByText('Загрузка…')).toBeNull();
  const request = (fetchMock.mock.calls[0] as unknown as [Request])[0];
  expect(new URL(request.url).pathname).toBe('/api/owner');
});

test('shows a clear Russian error when the backend answers with an error', async () => {
  vi.stubGlobal(
    'fetch',
    async () => Response.json({ code: 'INTERNAL_ERROR', message: 'Внутренняя ошибка сервера' }, { status: 500 }),
  );

  renderHome();

  const alert = await screen.findByRole('alert');
  expect(alert.textContent).toContain('Не удалось загрузить данные владельца');
  expect(screen.queryByText('Загрузка…')).toBeNull();
});

test('shows the same error when the network request fails', async () => {
  vi.stubGlobal('fetch', async () => {
    throw new TypeError('network down');
  });

  renderHome();

  const alert = await screen.findByRole('alert');
  expect(alert.textContent).toContain('Не удалось загрузить данные владельца');
});

test('meeting types stay visible regardless of the owner state', async () => {
  vi.stubGlobal('fetch', async () => Response.json({}, { status: 500 }));

  renderHome();

  expect(screen.getAllByRole('article').length).toBeGreaterThan(0);
  await screen.findByRole('alert');
});
