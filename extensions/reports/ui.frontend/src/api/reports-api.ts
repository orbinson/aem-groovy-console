import { config } from '@console/config';
import { delJson, getJson, getJsonWithError, postJson } from '@console/api/client';
import type {
  BrowseResponse,
  BrowseType,
  DistributionTarget,
  DistributorsResponse,
  DynamicOption,
  ReportDefinition,
  ReportExecution,
  ReportExecutionsResponse,
  ReportListResponse,
  ReportParameterValue,
  ReportPreviewResponse,
  ReportQueryAuditResponse,
  ReportsFeatureConfig,
  ResultPage,
  SaveReportRequest,
} from './reports-types';

const BASE = '/bin/groovyconsole/reports';

export function listReports(): Promise<ReportListResponse> {
  return getJsonWithError<ReportListResponse>(`${BASE}.json`);
}

/** Global reports feature flags (OSGi). Falls back to all-enabled if the endpoint is unavailable. */
export async function getReportsConfig(): Promise<ReportsFeatureConfig> {
  try {
    return await getJsonWithError<ReportsFeatureConfig>(`${BASE}/config.json`);
  } catch {
    return { schedulingEnabled: true, distributionEnabled: true };
  }
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
  values: Record<string, ReportParameterValue>,
): Promise<ReportPreviewResponse> {
  return postJson<ReportPreviewResponse>(`${BASE}/preview`, { ...definition, values });
}

/** Whether the optional query-audit extension is installed (so the editor can offer the "Audit queries" action). */
export async function isQueryAuditAvailable(): Promise<boolean> {
  try {
    const { available } = await getJson<{ available: boolean }>(`${BASE}/query-audit.json`);
    return available;
  } catch {
    return false;
  }
}

/** Run the (unsaved) report script with test values and report, per JCR query, whether Oak has a covering index. */
export function auditReportQueries(
  definition: SaveReportRequest,
  values: Record<string, ReportParameterValue>,
): Promise<ReportQueryAuditResponse> {
  return postJson<ReportQueryAuditResponse>(`${BASE}/query-audit`, { ...definition, values });
}

export function executeReport(
  name: string,
  parameters: Record<string, ReportParameterValue>,
): Promise<ReportExecution> {
  return postJson<ReportExecution>(`${BASE}/execute`, { name, parameters });
}

/** Available distributors and export formats, for the report editor's distribution section. */
export function listDistributors(): Promise<DistributorsResponse> {
  return getJsonWithError<DistributorsResponse>(`${BASE}/distributors.json`);
}

/** Distribute an already-completed successful execution on demand. */
export function distributeExecution(
  executionId: string,
  targets: DistributionTarget[],
): Promise<ReportExecution> {
  return postJson<ReportExecution>(`${BASE}/distribute`, { executionId, targets });
}

/** Resolve the options of a saved DYNAMIC parameter, passing the already-entered values it may depend on. */
export function resolveDynamicOptions(
  name: string,
  parameterName: string,
  parameters: Record<string, ReportParameterValue>,
): Promise<DynamicOption[]> {
  return postJson<{ options: DynamicOption[] }>(`${BASE}/options`, { name, parameterName, parameters }).then(
    (response) => response.options ?? [],
  );
}

/** Resolve options from an inline (unsaved) DYNAMIC script — used by the editor's "test" action. */
export function resolveDynamicOptionsForScript(
  script: string,
  parameters: Record<string, ReportParameterValue> = {},
): Promise<DynamicOption[]> {
  return postJson<{ options: DynamicOption[] }>(`${BASE}/options`, { script, parameters }).then(
    (response) => response.options ?? [],
  );
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

/** Download URL for an execution export; use as a plain link/window.location. */
export function exportUrl(executionId: string, format: string): string {
  const params = new URLSearchParams({ executionId, format });
  return `${config.contextPath}${BASE}/export?${params}`;
}

/** Deep link into the business reports UI (served by the reports bundle's page servlet). */
export function reportsPageUrl(hash = ''): string {
  return `${config.contextPath}/apps/groovyconsole/reports.html${hash}`;
}

/** Browse the children of a repository path for the PATH/TAG picker, filtered by browse type. */
export function browsePath(path: string, browseType: BrowseType = 'NODE'): Promise<BrowseResponse> {
  return getJsonWithError<BrowseResponse>(`${BASE}/browse.json`, { path, type: browseType });
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
