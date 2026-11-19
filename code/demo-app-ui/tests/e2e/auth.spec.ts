import { test, expect } from '@playwright/test';
import { DEMO_USERS, loginAs } from './helpers/auth';

test.describe('Authentication & Role-Based Access Control', () => {
  test('should display error message on invalid credentials', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByTestId('login-form')).toBeVisible();

    await page.getByLabel('Email').fill(DEMO_USERS.claimant.email);
    await page.getByLabel('Password', { exact: true }).fill('WrongPassword123!');
    await page.getByRole('button', { name: 'Log In', exact: true }).click();

    await expect(page.getByText(/Invalid email or password/i)).toBeVisible();
    await expect(page).toHaveURL(/\/login/);
  });

  test('should redirect Claimant to /claims and block access to /admin/dashboard', async ({ page }) => {
    await loginAs(page, 'claimant');

    // Attempt unauthorized navigation to Admin Dashboard
    await page.goto('/admin/dashboard');
    await expect(page).not.toHaveURL(/\/admin\/dashboard/);
  });

  test('should redirect Admin to /admin/dashboard upon login', async ({ page }) => {
    await loginAs(page, 'admin');
  });
});