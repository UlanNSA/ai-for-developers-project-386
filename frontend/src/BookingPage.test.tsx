import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeAll, expect, test, vi } from 'vitest';
import { client } from './api/generated/client.gen';
import { BookingPage } from './BookingPage';
import { backendStub, meetingTypes } from './testApi';

beforeAll(() => {
  client.setConfig({ baseUrl: 'http://localhost' });
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

function renderBooking(id: string) {
  render(
    <MemoryRouter initialEntries={[`/book/${id}`]}>
      <Routes>
        <Route path="/book/:meetingTypeId" element={<BookingPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

test('shows a loading state while the meeting type is being fetched', () => {
  vi.stubGlobal('fetch', () => new Promise<Response>(() => {}));

  renderBooking(meetingTypes[0].id);

  expect(screen.getByText('Загрузка…')).toBeTruthy();
});

test('shows the meeting type returned by GET /api/meeting-types/{id}', async () => {
  const fetchMock = vi.fn(backendStub());
  vi.stubGlobal('fetch', fetchMock);

  renderBooking(meetingTypes[1].id);

  expect(await screen.findByRole('heading', { level: 1, name: 'Консультация' })).toBeTruthy();
  expect(screen.getByText('30 мин')).toBeTruthy();
  expect(screen.getByText('Выбор времени будет доступен позже')).toBeTruthy();
  expect(screen.queryByText('Загрузка…')).toBeNull();
  const request = (fetchMock.mock.calls[0] as unknown as [Request])[0];
  expect(new URL(request.url).pathname).toBe(`/api/meeting-types/${meetingTypes[1].id}`);
});

test('unknown meeting type shows a not-found message with a link to the home page', async () => {
  vi.stubGlobal('fetch', backendStub());

  renderBooking('33333333-3333-4333-8333-333333333333');

  expect(await screen.findByText('Тип встречи не найден')).toBeTruthy();
  expect(screen.getByRole('link', { name: 'Вернуться на главную' }).getAttribute('href')).toBe('/');
  expect(screen.queryByText('Выбор времени будет доступен позже')).toBeNull();
});

test('a server failure shows an error, not the not-found message', async () => {
  vi.stubGlobal('fetch', async () =>
    Response.json({ code: 'INTERNAL_ERROR', message: 'Внутренняя ошибка сервера' }, { status: 500 }),
  );

  renderBooking(meetingTypes[0].id);

  const alert = await screen.findByRole('alert');
  expect(alert.textContent).toContain('Не удалось загрузить тип встречи');
  expect(screen.queryByText('Тип встречи не найден')).toBeNull();
  expect(screen.getByRole('link', { name: 'Вернуться на главную' })).toBeTruthy();
});

test('a network failure shows the same error', async () => {
  vi.stubGlobal('fetch', async () => {
    throw new TypeError('network down');
  });

  renderBooking(meetingTypes[0].id);

  expect((await screen.findByRole('alert')).textContent).toContain('Не удалось загрузить тип встречи');
});
