/**
 * Types for the migration extension HTTP API (/bin/groovyconsole/migration).
 */

export type MigrationStatus = 'SUCCESS' | 'FAILED' | 'SKIPPED' | 'RUNNING' | 'PENDING';

export interface MigrationRunSummary {
  runId: string;
  status: MigrationStatus;
  trigger: string;
  startDate: string;
  endDate: string;
  runningTime: string;
  /** Run-level error, set when an asynchronous run failed outside of script execution. */
  error: string;
  executed: number;
  failed: number;
  skipped: number;
  pending: number;
}

export interface MigrationScriptResult {
  scriptPath: string;
  checksum: string;
  status: MigrationStatus;
  runningTime: string;
  durationMillis: number;
  /** Truncated script output; full output is available in the console's audit history. */
  output: string;
  error: string;
}

export interface MigrationRun extends MigrationRunSummary {
  results: MigrationScriptResult[];
}

export interface MigrationRunsResponse {
  running: boolean;
  data: MigrationRunSummary[];
}

export interface MigrationScriptState {
  scriptPath: string;
  checksum: string;
  /** Empty string when the script has never been executed. */
  status: MigrationStatus | '';
  lastRunDate: string;
  runningTime: string;
  always: boolean;
  pending: boolean;
}

export interface MigrationRegistryResponse {
  data: MigrationScriptState[];
}

export interface MigrationPendingResponse {
  data: string[];
}

/** Response of an asynchronous trigger (HTTP 202). */
export interface MigrationQueuedResponse {
  runId: string;
  status: MigrationStatus;
}
