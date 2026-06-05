/**
 * Wait until the target instance reports healthy (Sling Starter boot + Groovy Console
 * content package installation finish after the feature launcher returns).
 */
export default async function globalSetup(): Promise<void> {
  const baseURL = process.env.GC_BASE_URL || 'http://localhost:8080';
  const username = process.env.GC_USERNAME || 'admin';
  const password = process.env.GC_PASSWORD || 'admin';
  const authorization = `Basic ${Buffer.from(`${username}:${password}`).toString('base64')}`;

  const deadline = Date.now() + 180_000;
  let lastError = 'no response';

  while (Date.now() < deadline) {
    try {
      const response = await fetch(`${baseURL}/system/health.json?tags=systemalive,groovyconsole`, {
        headers: { Authorization: authorization },
      });

      if (response.ok) {
        const json = (await response.json()) as { overallResult?: string };

        if (json.overallResult === 'OK') {
          await warmUpPages(baseURL, authorization, deadline);
          return;
        }
        lastError = `overallResult=${json.overallResult}`;
      } else {
        lastError = `HTTP ${response.status}`;
      }
    } catch (error) {
      lastError = String(error);
    }

    await new Promise((resolve) => setTimeout(resolve, 5_000));
  }

  throw new Error(`Instance at ${baseURL} not healthy in time: ${lastError}`);
}

/**
 * Request both UIs until they render successfully: shortly after startup, bundle refreshes
 * (Groovy fragment wiring) can make the first HTL compilation fail transiently with a 500.
 */
async function warmUpPages(baseURL: string, authorization: string, deadline: number): Promise<void> {
  const pages: Array<{ path: string; marker: string }> = [
    { path: '/apps/groovyconsole.classic.html', marker: 'script-editor' },
    { path: '/apps/groovyconsole.modern.html', marker: '__GC_CONFIG__' },
  ];

  for (const { path, marker } of pages) {
    let lastError = 'no response';

    while (Date.now() < deadline) {
      try {
        const response = await fetch(`${baseURL}${path}`, { headers: { Authorization: authorization } });
        const body = await response.text();

        if (response.ok && body.includes(marker)) {
          break;
        }
        lastError = `HTTP ${response.status}`;
      } catch (error) {
        lastError = String(error);
      }

      if (Date.now() >= deadline) {
        throw new Error(`Page ${path} did not render in time: ${lastError}`);
      }

      await new Promise((resolve) => setTimeout(resolve, 5_000));
    }
  }

  // warm the assist class dictionary so the first in-editor completion is instant
  await fetch(`${baseURL}/bin/groovyconsole/assist/classes?prefix=Object&limit=1`, {
    headers: { Authorization: authorization },
  }).catch(() => undefined);
}
