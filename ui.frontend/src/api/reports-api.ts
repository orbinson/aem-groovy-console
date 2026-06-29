import { config } from '../config';
import { delJson, getJson, getJsonWithError, postJson } from './client';
import type {
  BrowseResponse,
  ExportFormatsResponse,
  PathType,
  ReportDefinition,
  ReportExecution,
  ReportExecutionsResponse,
  ReportListResponse,
  ReportPreviewResponse,
  ResultPage,
  SaveReportRequest,
} from './reports-types';

const BASE = '/bin/groovyconsole/reports';

export function listReports(): Promise<ReportListResponse> {
  return getJsonWithError<ReportListResponse>(`${BASE}.json`);
}

export function getReport(name: string): Promise<ReportDefinition> {
  return getJsonWithError<ReportDefinition>(`${BASE}.json`, { name });
}

export function saveReport(definition: SaveReportRequest): Promise<ReportDefinition> {
  return postJson<ReportDefinition>(BASE, definition);
}

export function deleteReport(name: string): Promise<void> {
  return delJson(BASE, { name });
}

/** Ephemeral "try out" run of an (unsaved) report definition with test values — nothing is persisted. */
export function previewReport(
  definition: SaveReportRequest,
  values: Record<string, string>,
): Promise<ReportPreviewResponse> {
  return postJson<ReportPreviewResponse>(`${BASE}/preview`, { ...definition, values });
}

export function executeReport(name: string, parameters: Record<string, string>): Promise<ReportExecution> {
  return postJson<ReportExecution>(`${BASE}/execute`, { name, parameters });
}

export function getExecutions(name: string): Promise<ReportExecutionsResponse> {
  return getJsonWithError<ReportExecutionsResponse>(`${BASE}/executions.json`, { name });
}

export function getExecution(executionId: string): Promise<ReportExecution> {
  return getJsonWithError<ReportExecution>(`${BASE}/execution.json`, { executionId });
}

export function deleteExecution(executionId: string): Promise<void> {
  return delJson(`${BASE}/executions`, { executionId });
}

export function getResultPage(executionId: string, page: number, pageSize?: number): Promise<ResultPage> {
  const params: Record<string, string> = { executionId, page: String(page) };
  if (pageSize) {
    params.pageSize = String(pageSize);
  }
  return getJsonWithError<ResultPage>(`${BASE}/result.json`, params);
}

export function getExportFormats(): Promise<ExportFormatsResponse> {
  return getJsonWithError<ExportFormatsResponse>(`${BASE}/formats.json`);
}

/** Download URL for an execution export; use as a plain link/window.location. */
export function exportUrl(executionId: string, format: string): string {
  const params = new URLSearchParams({ executionId, format });
  return `${config.contextPath}${BASE}/export?${params}`;
}

/** Deep link into the business reports UI (served by the reports bundle's page servlet). */
export function reportsPageUrl(hash = ''): string {
  return `${config.contextPath}/apps/groovyconsole/reports.html${hash}`;
}

/** Browse the children of a repository path for the PATH parameter picker, filtered by pathType. */
export function browsePath(path: string, pathType: PathType = 'NODE'): Promise<BrowseResponse> {
  return getJsonWithError<BrowseResponse>(`${BASE}/browse.json`, { path, type: pathType });
}

/** List immediate child node paths under a repository path, for PATH parameter autocomplete.
 *  Uses the default .1.json rendering (AEM + Sling); returns [] on error/no access. */
export async function listChildPaths(parentPath: string): Promise<string[]> {
  const base = parentPath && parentPath !== '/' ? parentPath.replace(/\/$/, '') : '';
  try {
    const data = await getJson<Record<string, unknown>>(`${base || '/'}.1.json`);
    return Object.entries(data)
      .filter(([key, val]) => val !== null && typeof val === 'object' && !key.includes(':'))
      .map(([key]) => `${base}/${key}`)
      .sort();
  } catch {
    return [];
  }
}
