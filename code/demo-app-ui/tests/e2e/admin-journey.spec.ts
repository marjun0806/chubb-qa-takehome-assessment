import { test, expect } from '@playwright/test';

test.describe('Admin Claim Review & Status Management Journey', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Email').fill('admin@demo.com');
    await page.getByLabel('Password', { exact: true }).fill('Admin123!');
    await page.getByRole('button', { name: 'Log In', exact: true }).click();
    await page.waitForURL('**/admin/dashboard', { timeout: 15_000 });
  });

  test('should display dashboard metrics and allow navigating to admin claims management', async ({ page }) => {
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();

    await page.goto('/admin/claims');
    await expect(page).toHaveURL(/\/admin\/claims/);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  });
});