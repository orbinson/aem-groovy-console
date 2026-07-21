import { config } from '../config';

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

function url(path: string): string {
  return `${config.contextPath}${path}`;
}

let csrfToken: { value: string; fetchedAt: number } | null = null;

/** AEM's Granite CSRF filter requires a token for mutating requests from cookie-authenticated sessions. */
async function getCsrfToken(): Promise<string | null> {
  if (!config.aem) {
    return null;
  }

  if (csrfToken && Date.now() - csrfToken.fetchedAt < 60_000) {
    return csrfToken.value;
  }

  try {
    const response = await fetch(url('/libs/granite/csrf/token.json'), { credentials: 'same-origin' });

    if (!response.ok) {
      return null;
    }

    const { token } = (await response.json()) as { token?: string };

    // don't cache a missing/empty token — that would send "CSRF-Token: undefined" for the next 60s
    if (typeof token === 'string' && token.length > 0) {
      csrfToken = { value: token, fetchedAt: Date.now() };
      return token;
    }

    return null;
  } catch {
    return null;
  }
}

async function check(response: Response): Promise<Response> {
  if (!response.ok) {
    throw new ApiError(response.status, `${response.status} ${response.statusText}`);
  }
  return response;
}

/** Like check(), but surfaces the backend's JSON error message ({"error": "..."}) when present. */
async function checkWithErrorBody(response: Response): Promise<Response> {
  if (!response.ok) {
    let message = `${response.status} ${response.statusText}`;
    try {
      const body = (await response.json()) as { error?: string };
      if (body.error) {
        message = body.error;
      }
    } catch {
      // not a JSON error body
    }
    throw new ApiError(response.status, message);
  }
  return response;
}

export async function getJson<T>(path: string, params?: Record<string, string>): Promise<T> {
  const query = params ? `?${new URLSearchParams(params)}` : '';
  const response = await check(await fetch(`${url(path)}${query}`, { credentials: 'same-origin' }));
  return response.json() as Promise<T>;
}

export async function getText(path: string): Promise<string> {
  const response = await check(await fetch(url(path), { credentials: 'same-origin' }));
  return response.text();
}

/** Form-encoded POST, matching what the backend Sling servlets expect. */
export async function postForm<T>(path: string, form: Record<string, string | undefined>): Promise<T> {
  const body = new URLSearchParams();
  for (const [key, value] of Object.entries(form)) {
    if (value !== undefined) {
      body.append(key, value);
    }
  }

  const headers: Record<string, string> = { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' };
  const token = await getCsrfToken();
  if (token) {
    headers['CSRF-Token'] = token;
  }

  const response = await check(
    await fetch(url(path), {
      method: 'POST',
      credentials: 'same-origin',
      headers,
      body,
    }),
  );

  const text = await response.text();
  return (text ? JSON.parse(text) : {}) as T;
}

export async function del(path: string, params?: Record<string, string>): Promise<void> {
  const query = params ? `?${new URLSearchParams(params)}` : '';

  const headers: Record<string, string> = {};
  const token = await getCsrfToken();
  if (token) {
    headers['CSRF-Token'] = token;
  }

  await check(await fetch(`${url(path)}${query}`, { method: 'DELETE', credentials: 'same-origin', headers }));
}

/** JSON-body POST that surfaces the backend's JSON error message; sends a CSRF token on AEM. */
export async function postJson<T>(path: string, body: unknown): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json; charset=UTF-8' };
  const token = await getCsrfToken();
  if (token) {
    headers['CSRF-Token'] = token;
  }

  const response = await checkWithErrorBody(
    await fetch(url(path), {
      method: 'POST',
      credentials: 'same-origin',
      headers,
      body: JSON.stringify(body),
    }),
  );
  return response.json() as Promise<T>;
}

/** DELETE that surfaces the backend's JSON error message; sends a CSRF token on AEM. */
export async function delJson(path: string, params?: Record<string, string>): Promise<void> {
  const query = params ? `?${new URLSearchParams(params)}` : '';

  const headers: Record<string, string> = {};
  const token = await getCsrfToken();
  if (token) {
    headers['CSRF-Token'] = token;
  }

  await checkWithErrorBody(await fetch(`${url(path)}${query}`, { method: 'DELETE', credentials: 'same-origin', headers }));
}

/** GET that surfaces the backend's JSON error message. */
export async function getJsonWithError<T>(path: string, params?: Record<string, string>): Promise<T> {
  const query = params ? `?${new URLSearchParams(params)}` : '';
  const response = await checkWithErrorBody(await fetch(`${url(path)}${query}`, { credentials: 'same-origin' }));
  return response.json() as Promise<T>;
}
