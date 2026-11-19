import { FullConfig } from '@playwright/test';

async function globalSetup(config: FullConfig) {
  const baseURL = config.projects[0]?.use?.baseURL || 'http://localhost:3001';
  const bffHealthURL = 'http://localhost:8090/actuator/health';

  try {
    const bffRes = await fetch(bffHealthURL);
    if (!bffRes.ok) {
      throw new Error(`BFF health check failed with status ${bffRes.status}`);
    }
  } catch (error) {
    throw new Error(
      `Pre-flight check failed: BFF service is not reachable at ${bffHealthURL}. Ensure Docker stack is running. Details: ${error}`
    );
  }

  try {
    const uiRes = await fetch(`${baseURL}/login`);
    if (!uiRes.ok) {
      throw new Error(`UI check failed with status ${uiRes.status}`);
    }
  } catch (error) {
    throw new Error(
      `Pre-flight check failed: Demo UI is not reachable at ${baseURL}. Details: ${error}`
    );
  }
}

export default globalSetup;