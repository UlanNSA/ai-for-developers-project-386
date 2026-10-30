import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeAll, beforeEach, expect, test, vi } from 'vitest';
import { client } from './api/generated/client.gen';
import { App } from './App';

beforeAll(() => {
  client.setConfig({ baseUrl: 'http://localhost' });
});

beforeEach(() => {
  vi.stubGlobal('fetch', async () =>
    Response.json({ name: 'Тест', bio: 'О владельце', timeZone: 'Asia/Almaty' }),
  );
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

function renderAt(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

test('home page lists meeting types with title, description and duration', () => {
  renderAt('/');

  expect(screen.getByRole('heading', { level: 1, name: 'Календарь звонков' })).toBeTruthy();

  const cards = screen.getAllByRole('article');
  expect(cards.length).toBeGreaterThan(0);
  for (const card of cards) {
    expect(within(card).getByRole('heading', { level: 2 })).toBeTruthy();
    expect(within(card).getByText(/\d+ мин/)).toBeTruthy();
    expect(within(card).getByRole('link', { name: 'Выбрать время' })).toBeTruthy();
  }
});

test('every "Выбрать время" link points to its own /book/:meetingTypeId', () => {
  renderAt('/');

  const hrefs = screen
    .getAllByRole('link', { name: 'Выбрать время' })
    .map((link) => link.getAttribute('href'));

  expect(hrefs).toEqual(['/book/intro-call', '/book/consultation', '/book/workshop']);
});

test('home page does not show backend status', () => {
  renderAt('/');

  expect(screen.queryByText(/статус backend/i)).toBeNull();
});

test('"Выбрать время" opens the booking placeholder of that meeting type', () => {
  renderAt('/');
  const card = screen.getAllByRole('article')[0];
  const title = within(card).getByRole('heading', { level: 2 }).textContent ?? '';
  const duration = within(card).getByText(/\d+ мин/).textContent ?? '';

  fireEvent.click(within(card).getByRole('link', { name: 'Выбрать время' }));

  expect(screen.getByRole('heading', { level: 1, name: title })).toBeTruthy();
  expect(screen.getByText(duration)).toBeTruthy();
  expect(screen.getByText('Выбор времени будет доступен позже')).toBeTruthy();
});

test('booking placeholder links back to the home page', () => {
  renderAt('/book/intro-call');

  fireEvent.click(screen.getByRole('link', { name: 'Вернуться на главную' }));

  expect(screen.getByRole('heading', { level: 1, name: 'Календарь звонков' })).toBeTruthy();
});

test('unknown path redirects to the home page', () => {
  renderAt('/no/such/page');

  expect(screen.getByRole('heading', { level: 1, name: 'Календарь звонков' })).toBeTruthy();
});

test('unknown meeting type shows a not-found message with a way back', () => {
  renderAt('/book/does-not-exist');

  expect(screen.getByText('Тип встречи не найден')).toBeTruthy();
  expect(screen.queryByText('Выбор времени будет доступен позже')).toBeNull();
  expect(screen.getByRole('link', { name: 'Вернуться на главную' })).toBeTruthy();
});
