import { test, expect } from '@playwright/test';
import { loginAs } from './helpers/auth';

test.describe('Admin Claim Review & Status Management Journey', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, 'admin');
  });

  test('should display dashboard metrics and allow navigating to admin claims management', async ({ page }) => {
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();

    await page.goto('/admin/claims');
    await expect(page).toHaveURL(/\/admin\/claims/);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  });
});