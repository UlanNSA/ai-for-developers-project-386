import { type Page, expect, test } from './fixtures';

// Frozen "now" is Monday 2026-11-02 06:00Z = 11:00 in the Owner's zone (Asia/Almaty, UTC+5) = 10:00 in the
// guest zone (Asia/Tbilisi, UTC+4). The Owner works Mon-Fri 09:00-12:00 and 13:00-18:00 Almaty on a 15-minute grid.
// All expected clock times below are written out by hand in the guest zone (Almaty minus one hour):
//   - today (Mon 2 Nov) the first slot is 11:15 Almaty = 10:15 Tbilisi (11:00 is not strictly after now);
//   - tomorrow (Tue 3 Nov) the first slot is 09:00 Almaty = 08:00 Tbilisi.

const GUEST_CONTEXT = { baseURL: 'http://localhost:5173', timezoneId: 'Asia/Tbilisi', locale: 'ru-RU' };

async function openBooking(page: Page, meetingTitle: string) {
  await page.goto('/');
  await page.locator('article.card', { hasText: meetingTitle }).getByRole('link', { name: 'Выбрать время' }).click();
  await expect(page.getByRole('heading', { level: 1, name: meetingTitle })).toBeVisible();
}

async function pickSlot(page: Page, day: RegExp, time: string) {
  await page.getByRole('button', { name: day }).click();
  await page.getByRole('button', { name: time, exact: true }).click();
}

async function fillGuest(page: Page, name: string, email: string) {
  await page.getByLabel('Имя').fill(name);
  await page.getByLabel('Email').fill(email);
}

test.describe('Guest booking', () => {
  test('books a slot and sees the confirmation in the browser time zone, then returns home', async ({ page }) => {
    await openBooking(page, 'Консультация');
    await expect(page.getByText('Время указано в вашем часовом поясе: Asia/Tbilisi')).toBeVisible();

    await pickSlot(page, /вт, 3 ноября/, '08:00');
    await fillGuest(page, 'Анна Гость', 'anna@example.com');
    await page.getByRole('button', { name: 'Записаться' }).click();

    await expect(page.getByRole('heading', { level: 1, name: 'Вы записаны' })).toBeVisible();
    const confirmation = page.locator('dl.confirmation');
    await expect(confirmation).toContainText('Консультация');
    await expect(confirmation).toContainText('вторник, 3 ноября 2026');
    await expect(confirmation).toContainText('08:00–08:30 (ваш часовой пояс: Asia/Tbilisi)');
    await expect(confirmation).toContainText('30 мин');
    await expect(confirmation).toContainText('Анна Гость');
    await expect(confirmation).toContainText('anna@example.com');

    await page.getByRole('link', { name: 'Вернуться на главную' }).click();
    await expect(page).toHaveURL('/');
    await expect(page.getByRole('heading', { level: 1, name: 'Календарь звонков' })).toBeVisible();
  });

  test('two guests pick the same slot: the first books, the second gets a conflict and keeps the typed data', async ({
    browser,
  }) => {
    const firstContext = await browser.newContext(GUEST_CONTEXT);
    const secondContext = await browser.newContext(GUEST_CONTEXT);
    try {
      const first = await firstContext.newPage();
      const second = await secondContext.newPage();

      // Both select today's first slot, 10:15 Tbilisi (= 11:15 Almaty).
      await openBooking(first, 'Консультация');
      await pickSlot(first, /пн, 2 ноября/, '10:15');
      await fillGuest(first, 'Первый Гость', 'first@example.com');

      await openBooking(second, 'Знакомство');
      await pickSlot(second, /пн, 2 ноября/, '10:15');
      await fillGuest(second, 'Второй Гость', 'second@example.com');

      await first.getByRole('button', { name: 'Записаться' }).click();
      await expect(first.getByRole('heading', { level: 1, name: 'Вы записаны' })).toBeVisible();
      await expect(first.locator('dl.confirmation')).toContainText('10:15–10:45');

      await second.getByRole('button', { name: 'Записаться' }).click();
      await expect(second.getByRole('alert')).toHaveText('Это время уже занято, выберите другое');

      // The list refreshed: the 10:15-10:45 booking removes the 10:15 and 10:30 starts, 10:45 is the first free one.
      await expect(second.getByRole('button', { name: '10:15', exact: true })).toHaveCount(0);
      await expect(second.getByRole('button', { name: '10:30', exact: true })).toHaveCount(0);
      await expect(second.getByRole('button', { name: '10:45', exact: true })).toBeVisible();

      await second.getByRole('button', { name: '10:45', exact: true }).click();
      await expect(second.getByLabel('Имя')).toHaveValue('Второй Гость');
      await expect(second.getByLabel('Email')).toHaveValue('second@example.com');

      await second.getByRole('button', { name: 'Записаться' }).click();
      await expect(second.getByRole('heading', { level: 1, name: 'Вы записаны' })).toBeVisible();
      await expect(second.locator('dl.confirmation')).toContainText('10:45–11:00 (ваш часовой пояс: Asia/Tbilisi)');
    } finally {
      await firstContext.close();
      await secondContext.close();
    }
  });

  test('shows field errors for an empty name and an invalid email', async ({ page }) => {
    await openBooking(page, 'Знакомство');
    await pickSlot(page, /вт, 3 ноября/, '08:00');

    await page.getByRole('button', { name: 'Записаться' }).click();
    await expect(page.locator('#name-error')).toBeVisible();
    await expect(page.getByLabel('Имя')).toHaveAttribute('aria-invalid', 'true');
    await expect(page.getByLabel('Email')).toHaveAttribute('aria-invalid', 'true');
    await expect(page.locator('#email-error')).toBeVisible();

    await fillGuest(page, 'Анна Гость', 'not-an-email');
    await page.getByRole('button', { name: 'Записаться' }).click();
    await expect(page.locator('#email-error')).toBeVisible();
    await expect(page.locator('#name-error')).toHaveCount(0);
    await expect(page.getByLabel('Email')).toHaveAttribute('aria-invalid', 'true');
    await expect(page.getByRole('heading', { name: 'Вы записаны' })).toHaveCount(0);
  });
});

test.describe('Unknown addresses', () => {
  test('unknown meeting type shows "Тип встречи не найден" with a link home', async ({ page }) => {
    await page.goto('/book/00000000-0000-0000-0000-000000000000');
    await expect(page.getByRole('heading', { name: 'Тип встречи не найден' })).toBeVisible();
    await page.getByRole('link', { name: 'Вернуться на главную' }).click();
    await expect(page).toHaveURL('/');
    await expect(page.getByRole('heading', { level: 1, name: 'Календарь звонков' })).toBeVisible();
  });

  test('unknown path redirects to the home page', async ({ page }) => {
    await page.goto('/no/such/page');
    await expect(page).toHaveURL('/');
    await expect(page.getByRole('heading', { level: 1, name: 'Календарь звонков' })).toBeVisible();
  });
});
