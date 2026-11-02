import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeAll, expect, test, vi } from 'vitest';
import { client } from './api/generated/client.gen';
import { BookingPage } from './BookingPage';
import type { Slot } from './api/generated';
import { backendStub, meetingTypes } from './testApi';

// The guest time zone is fixed by `test.env.TZ` in vite.config.ts (Asia/Tbilisi, UTC+4, no DST).

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
  expect(screen.queryByText('Загрузка…')).toBeNull();
  const request = (fetchMock.mock.calls[0] as unknown as [Request])[0];
  expect(new URL(request.url).pathname).toBe(`/api/meeting-types/${meetingTypes[1].id}`);
});

test('unknown meeting type shows a not-found message with a link to the home page', async () => {
  vi.stubGlobal('fetch', backendStub());

  renderBooking('33333333-3333-4333-8333-333333333333');

  expect(await screen.findByText('Тип встречи не найден')).toBeTruthy();
  expect(screen.getByRole('link', { name: 'Вернуться на главную' }).getAttribute('href')).toBe('/');
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

function buttonLabels(container: Element | null) {
  return within(container as HTMLElement)
    .getAllByRole('button')
    .map((button) => button.textContent);
}

const daysList = async () => (await screen.findByRole('heading', { name: 'Выберите день' })).nextElementSibling;
const timesList = () => screen.getByRole('heading', { name: 'Выберите время' }).nextElementSibling;
const stubSlots = (freeSlots: Slot[]) => backendStub(meetingTypes, freeSlots);

test('requests the slots of the opened meeting type', async () => {
  const fetchMock = vi.fn(backendStub());
  vi.stubGlobal('fetch', fetchMock);

  renderBooking(meetingTypes[1].id);
  await screen.findByRole('heading', { name: 'Выберите день' });

  const paths = fetchMock.mock.calls.map((call) => new URL((call as unknown as [Request])[0].url).pathname);
  expect(paths).toContain(`/api/meeting-types/${meetingTypes[1].id}/slots`);
});

test('shows a loading state while slots are being fetched', async () => {
  vi.stubGlobal('fetch', (input: Request) =>
    input.url.endsWith('/slots') ? new Promise<Response>(() => {}) : backendStub()(input),
  );

  renderBooking(meetingTypes[0].id);

  expect(await screen.findByText('Загрузка свободного времени…')).toBeTruthy();
});

test('a slots failure shows an error but keeps the meeting type', async () => {
  vi.stubGlobal('fetch', async (input: Request) =>
    input.url.endsWith('/slots')
      ? Response.json({ code: 'INTERNAL_ERROR', message: 'Внутренняя ошибка сервера' }, { status: 500 })
      : backendStub()(input),
  );

  renderBooking(meetingTypes[0].id);

  expect((await screen.findByRole('alert')).textContent).toContain('Не удалось загрузить свободное время');
  expect(screen.getByRole('heading', { level: 1, name: meetingTypes[0].title })).toBeTruthy();
});

test('shows the guest time zone label', async () => {
  vi.stubGlobal('fetch', backendStub());

  renderBooking(meetingTypes[0].id);

  expect(await screen.findByText('Время указано в вашем часовом поясе: Asia/Tbilisi')).toBeTruthy();
});

test('picks a day first, then lists the times of that day in the guest time zone', async () => {
  vi.stubGlobal('fetch', backendStub());

  renderBooking(meetingTypes[0].id);

  expect(buttonLabels(await daysList())).toEqual(['пн, 2 ноября', 'вт, 3 ноября']);
  expect(screen.queryByRole('heading', { name: 'Выберите время' })).toBeNull();

  fireEvent.click(screen.getByRole('button', { name: 'пн, 2 ноября' }));
  expect(buttonLabels(timesList())).toEqual(['08:00', '08:15']);

  fireEvent.click(screen.getByRole('button', { name: '08:15' }));
  expect(screen.getByRole('button', { name: '08:15' }).getAttribute('aria-pressed')).toBe('true');

  fireEvent.click(screen.getByRole('button', { name: 'вт, 3 ноября' }));
  expect(buttonLabels(timesList())).toEqual(['08:00']);
});

test('groups by the guest calendar date, not by the UTC date', async () => {
  // 21:30 UTC on 2 Nov is 01:30 on 3 Nov in Asia/Tbilisi.
  vi.stubGlobal(
    'fetch',
    stubSlots([
      { startAt: '2026-11-02T10:00:00Z', endAt: '2026-11-02T10:15:00Z' },
      { startAt: '2026-11-02T21:30:00Z', endAt: '2026-11-02T21:45:00Z' },
    ]),
  );

  renderBooking(meetingTypes[0].id);

  expect(buttonLabels(await daysList())).toEqual(['пн, 2 ноября', 'вт, 3 ноября']);
  fireEvent.click(screen.getByRole('button', { name: 'вт, 3 ноября' }));
  expect(buttonLabels(timesList())).toEqual(['01:30']);
});

test('days without free slots are not shown', async () => {
  vi.stubGlobal(
    'fetch',
    stubSlots([
      { startAt: '2026-11-02T04:00:00Z', endAt: '2026-11-02T04:15:00Z' },
      { startAt: '2026-11-05T04:00:00Z', endAt: '2026-11-05T04:15:00Z' },
    ]),
  );

  renderBooking(meetingTypes[0].id);

  expect(buttonLabels(await daysList())).toEqual(['пн, 2 ноября', 'чт, 5 ноября']);
});

test('shows "Свободного времени нет" when there are no slots', async () => {
  vi.stubGlobal('fetch', stubSlots([]));

  renderBooking(meetingTypes[0].id);

  expect(await screen.findByText('Свободного времени нет')).toBeTruthy();
  expect(screen.queryByRole('heading', { name: 'Выберите день' })).toBeNull();
});
