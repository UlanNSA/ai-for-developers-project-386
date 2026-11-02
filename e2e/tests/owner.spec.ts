import type { APIRequestContext, Page } from '@playwright/test';
import { expect, test, TEST_HEADERS } from './fixtures';

// Frozen "now" is Monday 2026-11-02 06:00Z = 11:00 Asia/Almaty (UTC+5, the Owner's zone). The browser of these
// tests runs in Asia/Tbilisi (UTC+4), so every Owner-zone time below differs from the browser zone by an hour:
// if the page wrongly used the browser zone, the expected literals would not match.
//   Tue 3 Nov 09:00 Almaty = 2026-11-03T04:00:00Z      Tue 3 Nov 14:00 Almaty = 2026-11-03T09:00:00Z
//   Wed 4 Nov 10:00 Almaty = 2026-11-04T05:00:00Z

const GUEST_CONTEXT = { baseURL: 'http://localhost:5173', timezoneId: 'Asia/Tbilisi', locale: 'ru-RU' };

// The test routes and the setup bookings go straight to the loopback test backend (not proxied by Vite).
const BACKEND = 'http://127.0.0.1:8080';

const WARNING =
  'Учебная страница без авторизации. Адрес доступен всем, данные Гостей видны любому. Используйте только тестовые данные';

/**
 * Setup shortcut: books through the real backend API instead of the guest UI (the guest UI flow is covered
 * in guest.spec.ts); the Owner-side behaviour under test is then driven only through the browser.
 */
async function bookViaApi(
  request: APIRequestContext,
  meetingTitle: string,
  startAt: string,
  guestName: string,
  guestEmail: string,
) {
  const types = (await (await request.get(`${BACKEND}/api/meeting-types`)).json()) as { id: string; title: string }[];
  const meetingType = types.find((type) => type.title === meetingTitle);
  expect(meetingType, `seeded meeting type "${meetingTitle}"`).toBeDefined();
  const response = await request.post(`${BACKEND}/api/bookings`, {
    data: { meetingTypeId: meetingType?.id, startAt, guestName, guestEmail },
  });
  expect(response.status(), `POST /api/bookings ${startAt}`).toBe(201);
}

async function fillTypeForm(page: Page, fields: { title?: string; description?: string; duration?: string }) {
  if (fields.title !== undefined) await page.getByLabel('Название').fill(fields.title);
  if (fields.description !== undefined) await page.getByLabel('Описание').fill(fields.description);
  if (fields.duration !== undefined) await page.getByLabel('Длительность, мин').fill(fields.duration);
}

test.describe('Owner page and meeting types', () => {
  test('shows the no-auth warning, and the home page does not link to /owner', async ({ page }) => {
    await page.goto('/owner');
    await expect(page.getByRole('heading', { level: 1, name: 'Страница владельца' })).toBeVisible();
    await expect(page.getByRole('note')).toHaveText(WARNING);

    await page.goto('/');
    await expect(page.getByRole('heading', { level: 1, name: 'Календарь звонков' })).toBeVisible();
    await expect(page.locator('a[href="/owner"]')).toHaveCount(0);
    await expect(page.getByRole('link', { name: /владел/i })).toHaveCount(0);
  });

  test('creates a meeting type: it is listed for the Owner and visible to a Guest, whose booking page opens', async ({
    page,
    browser,
  }) => {
    await page.goto('/owner');
    await expect(page.locator('ul.owner-types li')).toHaveCount(2);

    await fillTypeForm(page, { title: 'Разбор проекта', description: 'Подробный разбор задачи', duration: '45' });
    await page.getByRole('button', { name: 'Создать' }).click();

    const created = page.locator('ul.owner-types li', { hasText: 'Разбор проекта' });
    await expect(created).toBeVisible();
    await expect(created).toContainText('45 мин');
    await expect(created).toContainText('Подробный разбор задачи');
    await expect(page.locator('ul.owner-types li')).toHaveCount(3);
    // The form is cleared after success.
    await expect(page.getByLabel('Название')).toHaveValue('');
    await expect(page.getByLabel('Длительность, мин')).toHaveValue('');

    const guestContext = await browser.newContext(GUEST_CONTEXT);
    try {
      const guest = await guestContext.newPage();
      await guest.goto('/');
      const card = guest.locator('article.card', { hasText: 'Разбор проекта' });
      await expect(card).toBeVisible();
      await expect(card.getByText('45 мин')).toBeVisible();
      await expect(card.getByText('Подробный разбор задачи')).toBeVisible();

      await card.getByRole('link', { name: 'Выбрать время' }).click();
      await expect(guest).toHaveURL(/\/book\/[0-9a-f-]{36}$/);
      await expect(guest.getByRole('heading', { level: 1, name: 'Разбор проекта' })).toBeVisible();
    } finally {
      await guestContext.close();
    }
  });
});

test.describe('Meeting type form validation', () => {
  test('empty title shows an error at the title field only', async ({ page }) => {
    await page.goto('/owner');
    await fillTypeForm(page, { title: '', duration: '30' });
    await page.getByRole('button', { name: 'Создать' }).click();

    await expect(page.locator('#title-error')).toContainText('Название');
    await expect(page.getByLabel('Название')).toHaveAttribute('aria-invalid', 'true');
    await expect(page.locator('#durationMinutes-error')).toHaveCount(0);
    await expect(page.locator('ul.owner-types li')).toHaveCount(2);
  });

  for (const [duration, reason] of [
    ['10', 'below 15'],
    ['20', 'not a multiple of 15'],
    ['255', 'above 240'],
  ] as const) {
    test(`duration ${duration} (${reason}) shows an error at the duration field and creates nothing`, async ({
      page,
    }) => {
      await page.goto('/owner');
      await fillTypeForm(page, { title: 'Новый тип', duration });
      await page.getByRole('button', { name: 'Создать' }).click();

      // The form is noValidate, so the native number-input rules do not block the request: the backend message shows.
      await expect(page.locator('#durationMinutes-error')).toContainText('кратное 15, от 15 до 240');
      await expect(page.getByLabel('Длительность, мин')).toHaveAttribute('aria-invalid', 'true');
      await expect(page.locator('#title-error')).toHaveCount(0);
      await expect(page.locator('ul.owner-types li')).toHaveCount(2);
      await expect(page.locator('ul.owner-types li', { hasText: 'Новый тип' })).toHaveCount(0);
    });
  }
});

test.describe('Owner upcoming bookings', () => {
  test('shows the empty state before any booking', async ({ page }) => {
    await page.goto('/owner');
    await expect(page.getByText('Предстоящих встреч нет')).toBeVisible();
    await expect(page.locator('ul.owner-bookings')).toHaveCount(0);
  });

  test('after a booking "Обновить" shows it in the Owner zone with the guest name and email', async ({
    page,
    request,
  }) => {
    await page.goto('/owner');
    await expect(page.getByText('Предстоящих встреч нет')).toBeVisible();

    await bookViaApi(request, 'Консультация', '2026-11-03T04:00:00Z', 'Анна Гость', 'anna@example.com');
    // No polling: the list stays as it was until the Owner presses the button.
    await expect(page.getByText('Предстоящих встреч нет')).toBeVisible();
    await page.getByRole('button', { name: 'Обновить' }).click();

    await expect(page.getByText('Время указано в часовом поясе Владельца: Asia/Almaty')).toBeVisible();
    const items = page.locator('ul.owner-bookings li');
    await expect(items).toHaveCount(1);
    await expect(items.first()).toContainText('Консультация');
    await expect(items.first()).toContainText('вт, 3 ноября 2026');
    await expect(items.first()).toContainText('09:00–09:30');
    await expect(items.first()).toContainText('Анна Гость, anna@example.com');
    await expect(page.getByText('Предстоящих встреч нет')).toHaveCount(0);
  });

  test('two bookings are listed ascending by start, whatever order they were made in', async ({ page, request }) => {
    // The later booking is made first.
    await bookViaApi(request, 'Консультация', '2026-11-04T05:00:00Z', 'Борис Гость', 'boris@example.com');
    await bookViaApi(request, 'Знакомство', '2026-11-03T09:00:00Z', 'Вера Гость', 'vera@example.com');

    await page.goto('/owner');

    const items = page.locator('ul.owner-bookings li');
    await expect(items).toHaveCount(2);
    await expect(items.nth(0)).toContainText('Знакомство');
    await expect(items.nth(0)).toContainText('вт, 3 ноября 2026');
    await expect(items.nth(0)).toContainText('14:00–14:15');
    await expect(items.nth(0)).toContainText('Вера Гость, vera@example.com');
    await expect(items.nth(1)).toContainText('Консультация');
    await expect(items.nth(1)).toContainText('ср, 4 ноября 2026');
    await expect(items.nth(1)).toContainText('10:00–10:30');
    await expect(items.nth(1)).toContainText('Борис Гость, boris@example.com');
  });

  test('a meeting whose start has passed (clock moved by the test route) disappears after "Обновить"', async ({
    page,
    request,
  }) => {
    // With a frozen clock a booking in the past is rejected by the public API, so the clock is moved after booking.
    await bookViaApi(request, 'Консультация', '2026-11-03T04:00:00Z', 'Анна Гость', 'anna@example.com');
    await bookViaApi(request, 'Знакомство', '2026-11-03T09:00:00Z', 'Вера Гость', 'vera@example.com');
    await page.goto('/owner');
    await expect(page.locator('ul.owner-bookings li')).toHaveCount(2);

    // 2026-11-03T04:01:00Z = 09:01 Almaty: the 09:00 meeting has started, the 14:00 one has not.
    const moved = await request.post(`${BACKEND}/__test__/clock`, {
      headers: TEST_HEADERS,
      data: { now: '2026-11-03T04:01:00Z' },
    });
    expect(moved.status()).toBe(204);
    await page.getByRole('button', { name: 'Обновить' }).click();

    const items = page.locator('ul.owner-bookings li');
    await expect(items).toHaveCount(1);
    await expect(items.first()).toContainText('Знакомство');
    await expect(page.getByText('Анна Гость')).toHaveCount(0);

    // Once nothing is upcoming, the empty state returns.
    await request.post(`${BACKEND}/__test__/clock`, { headers: TEST_HEADERS, data: { now: '2026-11-03T09:00:00Z' } });
    await page.getByRole('button', { name: 'Обновить' }).click();
    await expect(page.getByText('Предстоящих встреч нет')).toBeVisible();
  });
});
