// @ts-check
import { test, expect } from '@playwright/test';

test.describe('Agents view', () => {
  test('shows Create Agent form and Agents list', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('heading', { name: 'Create Agent', level: 3 })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Agents', level: 3 })).toBeVisible();
  });

  test('when no agents or API unavailable, shows empty state or error', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');
    await expect(
      page.getByText('No agents created yet').or(
        page.getByText('Loading agents...').or(
          page.getByText(/Error|Failed to load/).first()
        )
      )
    ).toBeVisible({ timeout: 15000 });
  });

  test('create agent form has required fields', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByPlaceholder('Arxiv hourly fetcher')).toBeVisible();
    await expect(
      page.getByPlaceholder(/Every hour, fetch the latest/)
    ).toBeVisible();
    await expect(page.getByRole('button', { name: /Create agent/i })).toBeVisible();
  });
});
