import { cleanup, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeAll, expect, test, vi } from 'vitest';
import { client } from './api/generated/client.gen';
import { HomePage } from './HomePage';
import { backendStub, meetingTypes, owner } from './testApi';

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

const pathOf = (input: Request | string | URL) =>
  new URL(input instanceof Request ? input.url : String(input)).pathname;

/** The backend stub with selected endpoints replaced. */
function stubWith(overrides: Record<string, () => Promise<Response>>) {
  const base = backendStub();
  vi.stubGlobal('fetch', (input: Request | string | URL) => (overrides[pathOf(input)] ?? (() => base(input)))());
}

const serverError = async () =>
  Response.json({ code: 'INTERNAL_ERROR', message: 'Внутренняя ошибка сервера' }, { status: 500 });

test('shows a loading state while data is being fetched', () => {
  vi.stubGlobal('fetch', () => new Promise<Response>(() => {}));

  renderHome();

  expect(screen.getAllByText('Загрузка…').length).toBeGreaterThan(0);
  expect(screen.queryAllByRole('article')).toHaveLength(0);
});

test('shows owner name and bio from GET /api/owner', async () => {
  const fetchMock = vi.fn(backendStub());
  vi.stubGlobal('fetch', fetchMock);

  renderHome();

  expect(await screen.findByRole('heading', { level: 2, name: owner.name })).toBeTruthy();
  expect(screen.getByText(owner.bio)).toBeTruthy();
  const paths = fetchMock.mock.calls.map(([input]) => pathOf(input));
  expect(paths).toContain('/api/owner');
});

test('shows a clear Russian error when the owner request answers with an error', async () => {
  stubWith({ '/api/owner': serverError });

  renderHome();

  const alert = await screen.findByText(/Не удалось загрузить данные владельца/);
  expect(alert.getAttribute('role')).toBe('alert');
});

test('shows the owner error when the network request fails', async () => {
  stubWith({
    '/api/owner': async () => {
      throw new TypeError('network down');
    },
  });

  renderHome();

  expect(await screen.findByText(/Не удалось загрузить данные владельца/)).toBeTruthy();
});

test('lists meeting types from GET /api/meeting-types with title, description, duration and link', async () => {
  const fetchMock = vi.fn(backendStub());
  vi.stubGlobal('fetch', fetchMock);

  renderHome();

  const cards = await screen.findAllByRole('article');
  expect(cards).toHaveLength(meetingTypes.length);
  expect(within(cards[0]).getByRole('heading', { level: 2, name: 'Знакомство' })).toBeTruthy();
  expect(within(cards[0]).getByText('Короткий разговор.')).toBeTruthy();
  expect(within(cards[0]).getByText('15 мин')).toBeTruthy();
  expect(within(cards[1]).getByText('30 мин')).toBeTruthy();
  const hrefs = screen.getAllByRole('link', { name: 'Выбрать время' }).map((link) => link.getAttribute('href'));
  expect(hrefs).toEqual(meetingTypes.map((type) => `/book/${type.id}`));
  expect(fetchMock.mock.calls.map(([input]) => pathOf(input))).toContain('/api/meeting-types');
});

test('shows a clear error when meeting types fail to load, and keeps the owner block', async () => {
  stubWith({ '/api/meeting-types': serverError });

  renderHome();

  const alert = await screen.findByText(/Не удалось загрузить типы встреч/);
  expect(alert.getAttribute('role')).toBe('alert');
  expect(screen.queryAllByRole('article')).toHaveLength(0);
  expect(await screen.findByRole('heading', { level: 2, name: owner.name })).toBeTruthy();
});

test('meeting types load even when the owner request fails', async () => {
  stubWith({ '/api/owner': serverError });

  renderHome();

  expect(await screen.findAllByRole('article')).toHaveLength(meetingTypes.length);
  expect(await screen.findByText(/Не удалось загрузить данные владельца/)).toBeTruthy();
});

test('shows a message when there are no meeting types', async () => {
  vi.stubGlobal('fetch', backendStub([]));

  renderHome();

  expect(await screen.findByText('Типов встреч пока нет')).toBeTruthy();
});
