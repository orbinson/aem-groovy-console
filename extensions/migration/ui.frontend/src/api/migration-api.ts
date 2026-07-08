import { config } from '@console/config';
import { getJson, postForm } from '@console/api/client';
import type {
  MigrationPendingResponse,
  MigrationQueuedResponse,
  MigrationRegistryResponse,
  MigrationRun,
  MigrationRunsResponse,
} from './migration-types';

const BASE = '/bin/groovyconsole/migration';

export function getRuns(): Promise<MigrationRunsResponse> {
  return getJson<MigrationRunsResponse>(`${BASE}.json`);
}

export function getRun(runId: string): Promise<MigrationRun> {
  return getJson<MigrationRun>(`${BASE}.json`, { runId });
}

export function getRegistry(): Promise<MigrationRegistryResponse> {
  return getJson<MigrationRegistryResponse>(`${BASE}.json`, { registry: 'true' });
}

export function getPending(): Promise<MigrationPendingResponse> {
  return getJson<MigrationPendingResponse>(`${BASE}.json`, { pending: 'true' });
}

/** Trigger a synchronous migration run; resolves with the aggregate result. Throws ApiError(409) when already running. */
export function runMigrations(): Promise<MigrationRun> {
  return postForm<MigrationRun>(BASE, {});
}

/** Enqueue an asynchronous migration run; poll {@link getRun} with the returned runId until terminal. */
export function queueMigrations(): Promise<MigrationQueuedResponse> {
  return postForm<MigrationQueuedResponse>(BASE, { async: 'true' });
}

/** Deep link into the migration history UI (served by the migration bundle's page servlet). */
export function migrationsPageUrl(hash = ''): string {
  return `${config.contextPath}/apps/groovyconsole/migrations.html${hash}`;
}
