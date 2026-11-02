import { expect, test } from './fixtures';

test('home page shows the owner and the seeded meeting types, "Выбрать время" opens the booking page', async ({
  page,
}) => {
  await page.goto('/');

  await expect(page.getByRole('heading', { level: 1, name: 'Календарь звонков' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Владелец календаря' })).toBeVisible();
  await expect(page.getByText('Выберите удобное время для встречи')).toBeVisible();

  const cards = page.locator('article.card');
  await expect(cards).toHaveCount(2);
  await expect(cards.nth(0).getByRole('heading', { name: 'Знакомство' })).toBeVisible();
  await expect(cards.nth(0).getByText('15 мин')).toBeVisible();
  await expect(cards.nth(1).getByRole('heading', { name: 'Консультация' })).toBeVisible();
  await expect(cards.nth(1).getByText('30 мин')).toBeVisible();

  await cards.nth(1).getByRole('link', { name: 'Выбрать время' }).click();

  await expect(page).toHaveURL(/\/book\/[0-9a-f-]{36}$/);
  await expect(page.getByRole('heading', { level: 1, name: 'Консультация' })).toBeVisible();
});
