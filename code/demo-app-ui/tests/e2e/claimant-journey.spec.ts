import { test, expect } from '@playwright/test';
import { loginAs } from './helpers/auth';

test.describe('Claimant End-to-End Claim Submission Journey', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, 'claimant');
  });

  test('should complete 3-step wizard and display newly submitted claim in list', async ({ page }) => {
    const uniqueSuffix = Date.now();
    const incidentLocation = `742 Evergreen Terrace #${uniqueSuffix}`;
    const description = `E2E automated test claim ${uniqueSuffix}: rear-end collision at intersection during wet road conditions.`;
    const uniqueAmount = (1000 + (uniqueSuffix % 8999) + 0.5).toFixed(2);
    const formattedAmount = new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
    }).format(Number(uniqueAmount));
    const today = new Date().toISOString().split('T')[0];

    // Navigate to New Claim Wizard
    await page.goto('/claims/new');
    const step1 = page.getByTestId('wizard-step-1');
    await expect(step1).toBeVisible();

    // Step 1: Incident Details (verify Next disabled until valid)
    const step1Next = step1.getByRole('button', { name: 'Next', exact: true });
    await expect(step1Next).toBeDisabled();

    await page.getByLabel(/When did the incident occur/i).fill(today);
    await page.getByLabel(/Where did the incident occur/i).fill(incidentLocation);
    await page.getByLabel(/Claim Amount/i).fill(uniqueAmount);

    await expect(step1Next).toBeEnabled();
    await step1Next.click();

    // Step 2: Supporting Details
    const step2 = page.getByTestId('wizard-step-2');
    await expect(step2).toBeVisible();
    await page.getByLabel(/Describe what happened/i).fill(description);
    await step2.getByRole('button', { name: 'Next', exact: true }).click();

    // Step 3: Review & Submit
    const step3 = page.getByTestId('wizard-step-3');
    await expect(step3).toBeVisible();
    await expect(page.getByText(incidentLocation)).toBeVisible();
    await expect(page.getByText(formattedAmount)).toBeVisible();
    await expect(page.getByText(description)).toBeVisible();

    await step3.getByRole('button', { name: 'Submit Claim', exact: true }).click();

    // Redirect back to /claims and verify new claim amount & SUBMITTED status are rendered
    await page.waitForURL('**/claims', { timeout: 15_000 });
    await expect(page.getByText(formattedAmount).first()).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText(/SUBMITTED/i).first()).toBeVisible();
  });
});