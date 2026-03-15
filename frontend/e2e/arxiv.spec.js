// @ts-check
import { test, expect } from '@playwright/test';

test.describe('Arxiv view', () => {
  test('shows hero and search when on Arxiv tab', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('tab', { name: 'Arxiv' }).click();
    await expect(page.getByRole('heading', { name: 'arXiv stored abstracts', level: 2 })).toBeVisible();
    await expect(
      page.getByPlaceholder(/Search title, authors, categories/)
    ).toBeVisible();
    await expect(page.getByRole('button', { name: 'Search' })).toBeVisible();
  });

  test('shows document count or empty message', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('tab', { name: 'Arxiv' }).click();
    await expect(
      page.getByText(/Showing|No arXiv documents found/).first()
    ).toBeVisible({ timeout: 10000 });
  });
});
