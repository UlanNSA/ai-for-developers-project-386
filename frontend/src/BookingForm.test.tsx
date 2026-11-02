import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeAll, expect, test, vi } from 'vitest';
import type { ApiError, Booking } from './api/generated';
import { client } from './api/generated/client.gen';
import { BookingPage } from './BookingPage';
import { backendStub, meetingTypes, slots } from './testApi';

// The guest time zone is fixed by `test.env.TZ` in vite.config.ts (Asia/Tbilisi, UTC+4, no DST).

beforeAll(() => {
  client.setConfig({ baseUrl: 'http://localhost' });
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

const meetingType = meetingTypes[1]; // 30 minutes
const firstSlot = slots[0]; // 2026-11-02 08:00 in Asia/Tbilisi

const created: Booking = {
  id: '44444444-4444-4444-8444-444444444444',
  meetingTypeId: meetingType.id,
  startAt: '2026-11-02T04:00:00Z',
  endAt: '2026-11-02T04:30:00Z',
  guestName: 'Анна',
  guestEmail: 'Anna@Example.com',
};

const apiError = (status: number, body: ApiError) => Response.json(body, { status });
const conflict = () => apiError(409, { code: 'SLOT_TAKEN', message: 'Это время уже занято' });

function renderBooking() {
  render(
    <MemoryRouter initialEntries={[`/book/${meetingType.id}`]}>
      <Routes>
        <Route path="/" element={<p>Главная</p>} />
        <Route path="/book/:meetingTypeId" element={<BookingPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

async function pickFirstSlot() {
  fireEvent.click(await screen.findByRole('button', { name: 'пн, 2 ноября' }));
  fireEvent.click(screen.getByRole('button', { name: '08:00' }));
}

function fill(name: string, email: string) {
  fireEvent.change(screen.getByLabelText('Имя'), { target: { value: name } });
  fireEvent.change(screen.getByLabelText('Email'), {
    target: { value: email },
  });
}

const submit = () => fireEvent.click(screen.getByRole('button', { name: 'Записаться' }));

function pathsOf(fetchMock: ReturnType<typeof vi.fn>) {
  return fetchMock.mock.calls.map((call) => new URL((call as unknown as [Request])[0].url).pathname);
}

test('the form appears only after a slot is selected', async () => {
  vi.stubGlobal('fetch', backendStub());

  renderBooking();
  await screen.findByRole('button', { name: 'пн, 2 ноября' });
  expect(screen.queryByLabelText('Имя')).toBeNull();

  await pickFirstSlot();

  expect(screen.getByLabelText('Имя')).toBeTruthy();
  expect(screen.getByLabelText('Email')).toBeTruthy();
  expect(screen.getByRole('button', { name: 'Записаться' })).toBeTruthy();
});

test('submits the selected slot with the typed data and shows the confirmation on the same screen', async () => {
  let sent: unknown;
  const fetchMock = vi.fn(
    backendStub(meetingTypes, slots, async (request) => {
      sent = await request.json();
      return Response.json(created, { status: 201 });
    }),
  );
  vi.stubGlobal('fetch', fetchMock);

  renderBooking();
  await pickFirstSlot();
  fill('Анна', 'Anna@Example.com');
  submit();

  expect(await screen.findByRole('heading', { name: 'Вы записаны' })).toBeTruthy();
  expect(sent).toEqual({
    meetingTypeId: meetingType.id,
    startAt: firstSlot.startAt,
    guestName: 'Анна',
    guestEmail: 'Anna@Example.com',
  });
  const details = (screen.getByText('Тип встречи').closest('dl') as HTMLElement).textContent;
  expect(details).toContain('Консультация');
  expect(details).toContain('понедельник, 2 ноября 2026');
  expect(details).toContain('08:00–08:30 (ваш часовой пояс: Asia/Tbilisi)');
  expect(details).toContain('30 мин');
  expect(details).toContain('Анна');
  expect(details).toContain('Anna@Example.com');
  expect(screen.queryByLabelText('Имя')).toBeNull();
  const home = screen.getAllByRole('link', { name: 'Вернуться на главную' });
  expect(home).toHaveLength(1);
  fireEvent.click(home[0]);
  expect(await screen.findByText('Главная')).toBeTruthy();
});

test('the button is disabled while the request is in flight and a second submit sends nothing', async () => {
  let release: (response: Response) => void = () => {};
  const fetchMock = vi.fn(
    backendStub(
      meetingTypes,
      slots,
      () =>
        new Promise<Response>((resolve) => {
          release = resolve;
        }),
    ),
  );
  vi.stubGlobal('fetch', fetchMock);

  renderBooking();
  await pickFirstSlot();
  fill('Анна', 'a@b.c');
  submit();

  const button = await screen.findByRole('button', { name: 'Запись…' });
  expect((button as HTMLButtonElement).disabled).toBe(true);
  fireEvent.submit(button.closest('form') as HTMLFormElement);
  expect(pathsOf(fetchMock).filter((path) => path === '/api/bookings')).toHaveLength(1);

  release(Response.json(created, { status: 201 }));
  expect(await screen.findByRole('heading', { name: 'Вы записаны' })).toBeTruthy();
});

test('field errors from the API are shown at the fields and the data is kept', async () => {
  vi.stubGlobal(
    'fetch',
    backendStub(meetingTypes, slots, () =>
      apiError(400, {
        code: 'VALIDATION_ERROR',
        message: 'Некорректные данные запроса',
        fieldErrors: [
          { field: 'guestName', message: 'Имя: от 1 до 100 символов' },
          { field: 'guestEmail', message: 'Email: некорректный адрес' },
        ],
      }),
    ),
  );

  renderBooking();
  await pickFirstSlot();
  fill('', 'nope');
  submit();

  const nameInput = screen.getByLabelText('Имя');
  await waitFor(() => {
    expect(nameInput.getAttribute('aria-invalid')).toBe('true');
  });
  expect(screen.getByText('Имя: от 1 до 100 символов')).toBeTruthy();
  expect(screen.getByText('Email: некорректный адрес')).toBeTruthy();
  expect(screen.getByLabelText('Email').getAttribute('aria-invalid')).toBe('true');
  expect((screen.getByLabelText('Email') as HTMLInputElement).value).toBe('nope');
  expect((screen.getByRole('button', { name: 'Записаться' }) as HTMLButtonElement).disabled).toBe(false);
});

test('a conflict shows the message, reloads the slots and keeps the typed name and email', async () => {
  const fetchMock = vi.fn(backendStub(meetingTypes, slots, conflict));
  vi.stubGlobal('fetch', fetchMock);

  renderBooking();
  await pickFirstSlot();
  fill('Анна', 'Anna@Example.com');
  const slotRequestsBefore = pathsOf(fetchMock).filter((path) => path.endsWith('/slots')).length;
  submit();

  expect((await screen.findByRole('alert')).textContent).toBe('Это время уже занято, выберите другое');
  await waitFor(() => {
    expect(pathsOf(fetchMock).filter((path) => path.endsWith('/slots'))).toHaveLength(slotRequestsBefore + 1);
  });
  // The taken slot is no longer selected; after choosing another one the typed data is still there.
  await screen.findByRole('button', { name: 'пн, 2 ноября' });
  expect(screen.queryByLabelText('Имя')).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: 'пн, 2 ноября' }));
  fireEvent.click(screen.getByRole('button', { name: '08:15' }));
  expect((screen.getByLabelText('Имя') as HTMLInputElement).value).toBe('Анна');
  expect((screen.getByLabelText('Email') as HTMLInputElement).value).toBe('Anna@Example.com');
  expect(screen.queryByText('Это время уже занято, выберите другое')).toBeNull();
});

test('a server failure shows a general error and keeps the form', async () => {
  vi.stubGlobal(
    'fetch',
    backendStub(meetingTypes, slots, () =>
      apiError(500, {
        code: 'INTERNAL_ERROR',
        message: 'Внутренняя ошибка сервера',
      }),
    ),
  );

  renderBooking();
  await pickFirstSlot();
  fill('Анна', 'a@b.c');
  submit();

  const alert = await screen.findByRole('alert');
  expect(alert.textContent).toContain('Не удалось записаться');
  expect((screen.getByLabelText('Имя') as HTMLInputElement).value).toBe('Анна');
  expect(within(document.body).getByRole('button', { name: 'Записаться' })).toBeTruthy();
});
