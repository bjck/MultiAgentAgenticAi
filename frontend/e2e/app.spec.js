// @ts-check
import { test, expect } from '@playwright/test';

test.describe('App shell', () => {
  test('loads and shows header with title', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('heading', { name: 'MultiAgent Builder', level: 1 })).toBeVisible();
  });

  test('has Agents, Arxiv, and Insights tabs', async ({ page }) => {
    await page.goto('/');
    const tablist = page.getByRole('tablist', { name: 'View selector' });
    await expect(tablist).toBeVisible();
    await expect(page.getByRole('tab', { name: 'Agents' })).toBeVisible();
    await expect(page.getByRole('tab', { name: 'Arxiv' })).toBeVisible();
    await expect(page.getByRole('tab', { name: 'Insights' })).toBeVisible();
  });

  test('Agents tab is active by default and shows agent console', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('tab', { name: 'Agents' })).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByRole('heading', { name: 'Self-running Agent Console', level: 2 })).toBeVisible();
  });

  test('switching to Arxiv tab shows arXiv view', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('tab', { name: 'Arxiv' }).click();
    await expect(page.getByRole('tab', { name: 'Arxiv' })).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByRole('heading', { name: 'arXiv stored abstracts', level: 2 })).toBeVisible();
  });

  test('switching to Insights tab shows dashboard', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('tab', { name: 'Insights' }).click();
    await expect(page.getByRole('tab', { name: 'Insights' })).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByRole('heading', { level: 2 })).toBeVisible();
  });
});
