import { test, expect } from '@playwright/test';

test.describe('Authentication & Role-Based Access Control', () => {
  test('should display error message on invalid credentials', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByTestId('login-form')).toBeVisible();

    await page.getByLabel('Email').fill('claimant@demo.com');
    await page.getByLabel('Password', { exact: true }).fill('WrongPassword123!');
    await page.getByRole('button', { name: 'Log In', exact: true }).click();

    await expect(page.getByText(/Invalid email or password/i)).toBeVisible();
    await expect(page).toHaveURL(/\/login/);
  });

  test('should redirect Claimant to /claims and block access to /admin/dashboard', async ({ page }) => {
    await page.goto('/login');

    await page.getByLabel('Email').fill('claimant@demo.com');
    await page.getByLabel('Password', { exact: true }).fill('Claimant123!');
    await page.getByRole('button', { name: 'Log In', exact: true }).click();

    await page.waitForURL('**/claims', { timeout: 15_000 });
    await expect(page).toHaveURL(/\/claims/);

    // Attempt unauthorized navigation to Admin Dashboard
    await page.goto('/admin/dashboard');
    await expect(page).not.toHaveURL(/\/admin\/dashboard/);
  });

  test('should redirect Admin to /admin/dashboard upon login', async ({ page }) => {
    await page.goto('/login');

    await page.getByLabel('Email').fill('admin@demo.com');
    await page.getByLabel('Password', { exact: true }).fill('Admin123!');
    await page.getByRole('button', { name: 'Log In', exact: true }).click();

    await page.waitForURL('**/admin/dashboard', { timeout: 15_000 });
    await expect(page).toHaveURL(/\/admin\/dashboard/);
  });
});