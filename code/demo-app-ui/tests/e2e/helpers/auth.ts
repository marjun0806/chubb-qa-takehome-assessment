import { Page, expect } from '@playwright/test';

export const DEMO_USERS = {
  claimant: {
    email: 'claimant@demo.com',
    password: 'Claimant123!',
    landingPath: '**/claims',
  },
  admin: {
    email: 'admin@demo.com',
    password: 'Admin123!',
    landingPath: '**/admin/dashboard',
  },
} as const;

export async function loginAs(page: Page, userType: keyof typeof DEMO_USERS) {
  const user = DEMO_USERS[userType];
  await page.goto('/login');
  await page.getByLabel('Email').fill(user.email);
  await page.getByLabel('Password', { exact: true }).fill(user.password);
  await page.getByRole('button', { name: 'Log In', exact: true }).click();
  await page.waitForURL(user.landingPath, { timeout: 15_000 });
  await expect(page).toHaveURL(new RegExp(user.landingPath.replace('**', '')));
}