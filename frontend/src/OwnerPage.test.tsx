import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeAll, expect, test, vi } from 'vitest';
import { type ApiError, type CreateMeetingTypeRequest, type MeetingType } from './api/generated';
import { client } from './api/generated/client.gen';
import { App } from './App';
import { OwnerPage } from './OwnerPage';
import { meetingTypes, owner } from './testApi';

beforeAll(() => {
  client.setConfig({ baseUrl: 'http://localhost' });
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

const WARNING =
  'Учебная страница без авторизации. Адрес доступен всем, данные Гостей видны любому. Используйте только тестовые данные';

type Handler = (body: CreateMeetingTypeRequest) => Response | Promise<Response>;

/** Stub of GET/POST /api/meeting-types with a growing list, like the real backend. */
function stubBackend(post: Handler, initial: MeetingType[] = meetingTypes) {
  const items = [...initial];
  const posted: CreateMeetingTypeRequest[] = [];
  const stub = vi.fn(async (input: Request | string | URL) => {
    const request = input instanceof Request ? input : new Request(String(input));
    const path = new URL(request.url).pathname;
    if (path === '/api/bookings') return Response.json([]);
    if (path === '/api/owner') return Response.json(owner);
    if (path !== '/api/meeting-types') return new Response('not found', { status: 404 });
    if (request.method === 'GET') return Response.json(items);
    const body = (await request.json()) as CreateMeetingTypeRequest;
    posted.push(body);
    return post(body);
  });
  vi.stubGlobal('fetch', stub);
  return { items, posted };
}

const created = (body: CreateMeetingTypeRequest): MeetingType => ({ id: '33333333-3333-4333-8333-333333333333', ...body });

const validationError = (...fieldErrors: { field: string; message: string }[]): Response => {
  const error: ApiError = { code: 'VALIDATION_ERROR', message: 'Некорректные данные запроса', fieldErrors };
  return Response.json(error, { status: 400 });
};

function renderOwner() {
  render(
    <MemoryRouter>
      <OwnerPage />
    </MemoryRouter>,
  );
}

const titleInput = () => screen.getByLabelText('Название');
const descriptionInput = () => screen.getByLabelText('Описание');
const durationInput = () => screen.getByLabelText('Длительность, мин');

function fillForm(title: string, description: string, duration: string) {
  fireEvent.change(titleInput(), { target: { value: title } });
  fireEvent.change(descriptionInput(), { target: { value: description } });
  fireEvent.change(durationInput(), { target: { value: duration } });
}

const submit = () => fireEvent.click(screen.getByRole('button', { name: 'Создать' }));

test('shows the permanent warning on top, even while loading', () => {
  vi.stubGlobal('fetch', () => new Promise<Response>(() => {}));

  renderOwner();

  const warning = screen.getByText(WARNING);
  expect(warning.closest('[role="note"], [role="alert"], p')).toBeTruthy();
  expect(screen.getByRole('heading', { level: 2, name: 'Типы встреч' })).toBeTruthy();
  expect(screen.getAllByText('Загрузка…').length).toBeGreaterThan(0);
});

test('lists existing meeting types', async () => {
  stubBackend(() => new Response(null, { status: 500 }));

  renderOwner();

  const items = await screen.findAllByRole('listitem');
  expect(items).toHaveLength(2);
  expect(within(items[0]).getByText('Знакомство')).toBeTruthy();
  expect(within(items[0]).getByText('15 мин')).toBeTruthy();
  expect(within(items[1]).getByText('30 мин')).toBeTruthy();
});

test('shows a message when there are no meeting types', async () => {
  stubBackend(() => new Response(null, { status: 500 }), []);

  renderOwner();

  expect(await screen.findByText('Типов встреч пока нет')).toBeTruthy();
});

test('shows an error when the list fails to load, the warning and the form stay', async () => {
  vi.stubGlobal('fetch', async () =>
    Response.json({ code: 'INTERNAL_ERROR', message: 'Внутренняя ошибка сервера' }, { status: 500 }),
  );

  renderOwner();

  const alert = await screen.findByText(/Не удалось загрузить типы встреч/);
  expect(alert.getAttribute('role')).toBe('alert');
  expect(screen.getByText(WARNING)).toBeTruthy();
  expect(screen.getByRole('button', { name: 'Создать' })).toBeTruthy();
});

test('duration is a number field with step 15 and minimum 15', async () => {
  stubBackend(() => new Response(null, { status: 500 }));

  renderOwner();
  await screen.findAllByRole('listitem');

  const input = durationInput();
  expect(input.getAttribute('type')).toBe('number');
  expect(input.getAttribute('step')).toBe('15');
  expect(input.getAttribute('min')).toBe('15');
});

test('creating a meeting type sends the form, clears it and refreshes the list', async () => {
  const backend = stubBackend((body) => {
    const item = created(body);
    backend.items.push(item);
    return Response.json(item, { status: 201 });
  });

  renderOwner();
  await screen.findAllByRole('listitem');
  fillForm('Интервью', 'Разговор', '45');
  submit();

  await waitFor(() => expect(screen.getAllByRole('listitem')).toHaveLength(3));
  expect(backend.posted).toEqual([{ title: 'Интервью', description: 'Разговор', durationMinutes: 45 }]);
  expect(screen.getByText('Интервью')).toBeTruthy();
  expect((titleInput() as HTMLInputElement).value).toBe('');
  expect((descriptionInput() as HTMLTextAreaElement).value).toBe('');
  expect((durationInput() as HTMLInputElement).value).toBe('');
});

test('an empty description is not sent', async () => {
  const backend = stubBackend((body) => Response.json(created(body), { status: 201 }));

  renderOwner();
  await screen.findAllByRole('listitem');
  fillForm('Без описания', '', '15');
  submit();

  await waitFor(() => expect(backend.posted).toHaveLength(1));
  expect(backend.posted[0]).toEqual({ title: 'Без описания', durationMinutes: 15 });
});

test('validation errors appear next to their fields and the input is kept', async () => {
  stubBackend(() =>
    validationError(
      { field: 'title', message: 'Название: от 1 до 100 символов' },
      { field: 'durationMinutes', message: 'Длительность: кратное 15' },
    ),
  );

  renderOwner();
  await screen.findAllByRole('listitem');
  fillForm('', 'Описание', '14');
  submit();

  const titleError = await screen.findByText('Название: от 1 до 100 символов');
  expect(titleInput().getAttribute('aria-describedby')).toBe(titleError.id);
  expect(titleInput().getAttribute('aria-invalid')).toBe('true');
  const durationError = screen.getByText('Длительность: кратное 15');
  expect(durationInput().getAttribute('aria-describedby')).toBe(durationError.id);
  expect(descriptionInput().getAttribute('aria-invalid')).not.toBe('true');
  expect((descriptionInput() as HTMLTextAreaElement).value).toBe('Описание');
  expect((durationInput() as HTMLInputElement).value).toBe('14');
  expect(screen.getAllByRole('listitem')).toHaveLength(2);
});

test('a description error appears next to the description', async () => {
  stubBackend(() => validationError({ field: 'description', message: 'Описание: не более 500 символов' }));

  renderOwner();
  await screen.findAllByRole('listitem');
  fillForm('t', 'd', '30');
  submit();

  const error = await screen.findByText('Описание: не более 500 символов');
  expect(descriptionInput().getAttribute('aria-describedby')).toBe(error.id);
});

test('errors are cleared after a successful resubmission', async () => {
  let attempt = 0;
  stubBackend((body) =>
    attempt++ === 0
      ? validationError({ field: 'title', message: 'Название: от 1 до 100 символов' })
      : Response.json(created(body), { status: 201 }),
  );

  renderOwner();
  await screen.findAllByRole('listitem');
  fillForm('', '', '30');
  submit();
  await screen.findByText('Название: от 1 до 100 символов');
  fireEvent.change(titleInput(), { target: { value: 'Ok' } });
  submit();

  await waitFor(() => expect(screen.queryByText('Название: от 1 до 100 символов')).toBeNull());
  expect(titleInput().getAttribute('aria-invalid')).not.toBe('true');
});

test('a server failure shows a general error and keeps the input', async () => {
  stubBackend(() =>
    Response.json({ code: 'INTERNAL_ERROR', message: 'Внутренняя ошибка сервера' }, { status: 500 }),
  );

  renderOwner();
  await screen.findAllByRole('listitem');
  fillForm('Title', '', '30');
  submit();

  const alert = await screen.findByText(/Не удалось создать тип встречи/);
  expect(alert.getAttribute('role')).toBe('alert');
  expect((titleInput() as HTMLInputElement).value).toBe('Title');
});

test('a network failure shows a general error', async () => {
  let creating = false;
  vi.stubGlobal('fetch', async (input: Request | string | URL) => {
    const request = input instanceof Request ? input : new Request(String(input));
    if (request.method === 'POST') {
      creating = true;
      throw new TypeError('network down');
    }
    const path = new URL(request.url).pathname;
    if (path === '/api/bookings') return Response.json([]);
    if (path === '/api/owner') return Response.json(owner);
    return Response.json(meetingTypes);
  });

  renderOwner();
  await screen.findAllByRole('listitem');
  fillForm('Title', '', '30');
  submit();

  expect(await screen.findByText(/Не удалось создать тип встречи/)).toBeTruthy();
  expect(creating).toBe(true);
});

test('the button is disabled while the request is in flight', async () => {
  let release: (response: Response) => void = () => {};
  stubBackend(() => new Promise<Response>((resolve) => (release = resolve)));

  renderOwner();
  await screen.findAllByRole('listitem');
  fillForm('Title', '', '30');
  submit();

  const button = await screen.findByRole('button', { name: 'Создание…' });
  expect((button as HTMLButtonElement).disabled).toBe(true);
  release(Response.json(created({ title: 'Title', durationMinutes: 30 }), { status: 201 }));
  await screen.findByRole('button', { name: 'Создать' });
});

test('the /owner route renders the page and the home page does not link to it', async () => {
  vi.stubGlobal('fetch', async (input: Request | string | URL) => {
    const path = new URL(input instanceof Request ? input.url : String(input)).pathname;
    if (path === '/api/owner') return Response.json({ name: 'Анна', bio: 'Био', timeZone: 'Asia/Almaty' });
    if (path === '/api/bookings') return Response.json([]);
    return Response.json(meetingTypes);
  });

  render(
    <MemoryRouter initialEntries={['/']}>
      <App />
    </MemoryRouter>,
  );
  await screen.findAllByRole('article');
  expect(screen.queryAllByRole('link').filter((link) => link.getAttribute('href')?.includes('owner'))).toHaveLength(0);
  cleanup();

  render(
    <MemoryRouter initialEntries={['/owner']}>
      <App />
    </MemoryRouter>,
  );
  expect(screen.getByText(WARNING)).toBeTruthy();
  await screen.findAllByRole('listitem');
});
