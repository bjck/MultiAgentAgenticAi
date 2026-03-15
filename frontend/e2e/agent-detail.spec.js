// @ts-check
import { test, expect } from '@playwright/test';

test.describe('Agent detail and run', () => {
  test('when no agent selected, shows placeholder', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByText('Select an agent to view details.').first()).toBeVisible({ timeout: 10000 });
  });

  test('create agent then select and see detail with Runs section', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('networkidle');
    const hasAgentsList = await page.locator('div.card-library').getByRole('heading', { name: 'Agents' }).isVisible().catch(() => false);
    test.skip(!hasAgentsList, 'Backend unavailable; start backend for this test');

    const name = `E2E Agent ${Date.now()}`;
    await page.getByPlaceholder('Arxiv hourly fetcher').fill(name);
    await page.getByPlaceholder(/Every hour, fetch the latest/).fill('Summarize the weather.');
    await page.getByRole('button', { name: 'Create agent' }).click();

    await expect(page.getByRole('button', { name: 'Creating...' })).toBeVisible().catch(() => {});
    await expect(page.getByRole('heading', { name, level: 4 }).or(page.getByText(name).first())).toBeVisible({
      timeout: 15000,
    });

    await page.getByText(name).first().click();

    await expect(page.getByRole('heading', { name: 'Runs', level: 4 })).toBeVisible({ timeout: 5000 });
    await expect(
      page.getByRole('button', { name: 'Run now' }).or(page.getByText('Run the agent to capture events.'))
    ).toBeVisible({ timeout: 5000 });
  });

  test('Run now adds a run and detail updates via WebSocket', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('networkidle');
    const hasAgentsList = await page.locator('div.card-library').getByRole('heading', { name: 'Agents' }).isVisible().catch(() => false);
    test.skip(!hasAgentsList, 'Backend unavailable; start backend for this test');

    const name = `E2E Run ${Date.now()}`;
    await page.getByPlaceholder('Arxiv hourly fetcher').fill(name);
    await page.getByPlaceholder(/Every hour, fetch the latest/).fill('Say hello once.');
    await page.getByRole('button', { name: 'Create agent' }).click();

    await expect(page.getByText(name).first()).toBeVisible({ timeout: 15000 });
    await page.getByText(name).first().click();

    await expect(page.getByRole('heading', { name: 'Runs', level: 4 })).toBeVisible({ timeout: 5000 });
    const runNowBtn = page.getByRole('button', { name: 'Run now' });
    await expect(runNowBtn).toBeVisible({ timeout: 5000 });

    const runsSection = page.locator('div.dashboard-section').filter({ has: page.getByRole('heading', { name: 'Runs' }) });
    await runNowBtn.click();

    await expect(page.getByRole('button', { name: 'Running...' })).toBeVisible().catch(() => {});

    await expect(runsSection.getByText(/RUNNING|SUCCEEDED|FAILED/)).toBeVisible({ timeout: 30000 });
  });
});
