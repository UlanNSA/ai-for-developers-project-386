import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeAll, beforeEach, expect, test, vi } from 'vitest';
import { client } from './api/generated/client.gen';
import { App } from './App';
import { backendStub, meetingTypes } from './testApi';

beforeAll(() => {
  client.setConfig({ baseUrl: 'http://localhost' });
});

beforeEach(() => {
  vi.stubGlobal('fetch', backendStub());
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

test('home page shows the service title and meeting type cards', async () => {
  renderAt('/');

  expect(screen.getByRole('heading', { level: 1, name: 'Календарь звонков' })).toBeTruthy();
  expect(await screen.findAllByRole('article')).toHaveLength(meetingTypes.length);
});

test('home page does not show backend status', async () => {
  renderAt('/');
  await screen.findAllByRole('article');

  expect(screen.queryByText(/статус backend/i)).toBeNull();
});

test('"Выбрать время" opens the booking page of that meeting type', async () => {
  renderAt('/');
  const card = (await screen.findAllByRole('article'))[0];

  fireEvent.click(within(card).getByRole('link', { name: 'Выбрать время' }));

  expect(await screen.findByRole('heading', { level: 1, name: meetingTypes[0].title })).toBeTruthy();
  expect(screen.getByText('15 мин')).toBeTruthy();
  expect(screen.getByText('Выбор времени будет доступен позже')).toBeTruthy();
});

test('booking page links back to the home page', async () => {
  renderAt(`/book/${meetingTypes[0].id}`);
  await screen.findByRole('heading', { level: 1, name: meetingTypes[0].title });

  fireEvent.click(screen.getByRole('link', { name: 'Вернуться на главную' }));

  expect(screen.getByRole('heading', { level: 1, name: 'Календарь звонков' })).toBeTruthy();
  await screen.findAllByRole('article');
});

test('unknown path redirects to the home page', async () => {
  renderAt('/no/such/page');

  expect(screen.getByRole('heading', { level: 1, name: 'Календарь звонков' })).toBeTruthy();
  await screen.findAllByRole('article');
});

test('unknown meeting type shows a not-found message with a way back', async () => {
  renderAt('/book/does-not-exist');

  expect(await screen.findByText('Тип встречи не найден')).toBeTruthy();
  expect(screen.getByRole('link', { name: 'Вернуться на главную' })).toBeTruthy();
});
