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

async function check(response: Response): Promise<Response> {
  if (!response.ok) {
    throw new ApiError(response.status, `${response.status} ${response.statusText}`);
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

  const response = await check(
    await fetch(url(path), {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
      body,
    }),
  );

  const text = await response.text();
  return (text ? JSON.parse(text) : {}) as T;
}

export async function del(path: string, params?: Record<string, string>): Promise<void> {
  const query = params ? `?${new URLSearchParams(params)}` : '';
  await check(await fetch(`${url(path)}${query}`, { method: 'DELETE', credentials: 'same-origin' }));
}
