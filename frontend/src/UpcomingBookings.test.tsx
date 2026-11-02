import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeAll, expect, test, vi } from 'vitest';
import type { Booking } from './api/generated';
import { client } from './api/generated/client.gen';
import { OwnerPage } from './OwnerPage';
import { meetingTypes, owner } from './testApi';
import { UpcomingBookings } from './UpcomingBookings';

beforeAll(() => {
  client.setConfig({ baseUrl: 'http://localhost' });
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

// Vitest runs with TZ=Asia/Tbilisi (UTC+4); the owner is in Asia/Almaty (UTC+5), so a wrong zone shows 08:00.
const upcoming: Booking[] = [
  {
    id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    meetingTypeId: meetingTypes[1].id,
    startAt: '2026-11-03T04:00:00Z',
    endAt: '2026-11-03T04:30:00Z',
    guestName: 'Борис',
    guestEmail: 'boris@example.com',
  },
  {
    id: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    meetingTypeId: meetingTypes[0].id,
    startAt: '2026-11-04T04:15:00Z',
    endAt: '2026-11-04T04:30:00Z',
    guestName: 'Вера',
    guestEmail: 'vera@example.com',
  },
];

const serverError = () => Response.json({ code: 'INTERNAL_ERROR', message: 'x' }, { status: 500 });

/** Stub of GET /api/bookings (a queue of responses, the last one repeats) and GET /api/owner. */
function stubBackend(...bookingResponses: (() => Response | Promise<Response>)[]) {
  let calls = 0;
  const stub = vi.fn(async (input: Request | string | URL) => {
    const path = new URL(input instanceof Request ? input.url : String(input)).pathname;
    if (path === '/api/owner') return Response.json(owner);
    if (path === '/api/meeting-types') return Response.json(meetingTypes);
    if (path === '/api/bookings') return bookingResponses[Math.min(calls++, bookingResponses.length - 1)]();
    return new Response('not found', { status: 404 });
  });
  vi.stubGlobal('fetch', stub);
  return () =>
    stub.mock.calls.filter(([input]) => String(input instanceof Request ? input.url : input).endsWith('/api/bookings'));
}

const renderBlock = () => render(<UpcomingBookings meetingTypes={meetingTypes} />);

test('shows the empty state', async () => {
  stubBackend(() => Response.json([]));

  renderBlock();

  expect(await screen.findByText('Предстоящих встреч нет')).toBeTruthy();
  expect(screen.getByRole('heading', { name: 'Предстоящие встречи' })).toBeTruthy();
});

test('shows a loading state first', () => {
  vi.stubGlobal('fetch', () => new Promise<Response>(() => {}));

  renderBlock();

  expect(screen.getByText('Загрузка…')).toBeTruthy();
});

test('lists bookings with type title, owner-zone time with a label, guest name and email', async () => {
  stubBackend(() => Response.json(upcoming));

  renderBlock();

  const items = await screen.findAllByRole('listitem');
  expect(items).toHaveLength(2);
  expect(within(items[0]).getByText('Консультация')).toBeTruthy();
  expect(items[0].textContent).toContain('09:00–09:30');
  expect(items[0].textContent).not.toContain('08:00');
  expect(items[0].textContent).toContain('3 ноября 2026');
  expect(within(items[0]).getByText(/Борис/)).toBeTruthy();
  expect(within(items[0]).getByText(/boris@example\.com/)).toBeTruthy();
  expect(within(items[1]).getByText('Знакомство')).toBeTruthy();
  expect(items[1].textContent).toContain('09:15–09:30');
  expect(screen.getByText('Время указано в часовом поясе Владельца: Asia/Almaty')).toBeTruthy();
});

test('the refresh button reloads the list', async () => {
  const requests = stubBackend(
    () => Response.json([]),
    () => Response.json(upcoming),
  );
  renderBlock();
  await screen.findByText('Предстоящих встреч нет');

  fireEvent.click(screen.getByRole('button', { name: 'Обновить' }));

  expect(await screen.findAllByRole('listitem')).toHaveLength(2);
  expect(requests()).toHaveLength(2);
});

test('the refresh button is disabled while the request is in flight', async () => {
  let release: (response: Response) => void = () => {};
  stubBackend(
    () => Response.json([]),
    () => new Promise<Response>((resolve) => (release = resolve)),
  );
  renderBlock();
  await screen.findByText('Предстоящих встреч нет');
  expect((screen.getByRole('button', { name: 'Обновить' }) as HTMLButtonElement).disabled).toBe(false);

  fireEvent.click(screen.getByRole('button', { name: 'Обновить' }));

  await waitFor(() =>
    expect((screen.getByRole('button', { name: 'Обновить' }) as HTMLButtonElement).disabled).toBe(true),
  );
  release(Response.json(upcoming));
  await screen.findAllByRole('listitem');
  expect((screen.getByRole('button', { name: 'Обновить' }) as HTMLButtonElement).disabled).toBe(false);
});

test('shows an error and lets the owner retry with the refresh button', async () => {
  stubBackend(serverError, () => Response.json(upcoming));

  renderBlock();

  const alert = await screen.findByRole('alert');
  expect(alert.textContent).toContain('Не удалось загрузить предстоящие встречи');
  fireEvent.click(screen.getByRole('button', { name: 'Обновить' }));
  expect(await screen.findAllByRole('listitem')).toHaveLength(2);
  expect(screen.queryByRole('alert')).toBeNull();
});

test('does not poll in the background', async () => {
  vi.useFakeTimers({ toFake: ['setInterval', 'setTimeout'] });
  try {
    const requests = stubBackend(() => Response.json([]));
    renderBlock();
    await vi.advanceTimersByTimeAsync(0);
    await vi.advanceTimersByTimeAsync(10 * 60 * 1000);
    expect(requests()).toHaveLength(1);
  } finally {
    vi.useRealTimers();
  }
});

test('on /owner the block sits above the meeting types', async () => {
  stubBackend(() => Response.json(upcoming));

  render(
    <MemoryRouter>
      <OwnerPage />
    </MemoryRouter>,
  );

  await screen.findByText(/Борис/);
  const headings = screen.getAllByRole('heading', { level: 2 }).map((heading) => heading.textContent);
  expect(headings).toEqual(['Предстоящие встречи', 'Типы встреч']);
  expect(within(screen.getAllByRole('listitem')[0]).getByText('Консультация')).toBeTruthy();
});
