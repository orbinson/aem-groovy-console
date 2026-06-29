/**
 * Types for the reports extension HTTP API (/bin/groovyconsole/reports*).
 * Contract: docs/reports-frontend-requirements.md
 */

export type ReportColumnType = 'STRING' | 'NUMBER' | 'DATE' | 'BOOLEAN' | 'LINK';

export type ReportParameterType = 'STRING' | 'NUMBER' | 'BOOLEAN' | 'DATE' | 'SELECT' | 'PATH';

/** What a PATH parameter's path browser shows. */
export type PathType = 'NODE' | 'PAGE' | 'ASSET';

export interface BrowseNode {
  name: string;
  path: string;
  title?: string | null;
  primaryType?: string | null;
  hasChildren: boolean;
  selectable: boolean;
}

export interface BrowseResponse {
  path: string;
  children: BrowseNode[];
  truncated?: boolean;
}

export type ReportExecutionStatus = 'RUNNING' | 'SUCCESS' | 'FAILED';

export interface ExportFormat {
  format: string;
  contentType: string;
  fileExtension: string;
}

export interface ReportSummary {
  name: string;
  title: string;
  description?: string | null;
  category?: string | null;
  /** Whether the user has JCR write access to the report node (edit/delete). */
  canEdit: boolean;
}

export interface ReportListResponse {
  reports: ReportSummary[];
  /** Whether the user may create reports (JCR write access to the reports folder). */
  canManage: boolean;
}

export interface ReportParameter {
  name: string;
  label: string;
  type: ReportParameterType;
  defaultValue?: string | null;
  required: boolean;
  options: string[];
  /** For PATH parameters: what the path browser shows. */
  pathType?: PathType;
  /** For PATH parameters: the path the browser is rooted at. */
  rootPath?: string | null;
  order: number;
}

export interface ReportDefinition extends ReportSummary {
  /** Inline Groovy script. */
  script?: string | null;
  pageSize?: number | null;
  parameters: ReportParameter[];
  created?: string | null;
  createdBy?: string | null;
  lastModified?: string | null;
  lastModifiedBy?: string | null;
  exportFormats: ExportFormat[];
}

/** Definition payload for create/update (read-only fields omitted). */
export interface SaveReportRequest {
  name: string;
  title?: string;
  description?: string;
  category?: string;
  script?: string;
  pageSize?: number;
  parameters?: Array<Omit<ReportParameter, 'order'> & { order?: number }>;
}

export interface ReportExecution {
  executionId: string;
  reportName: string;
  status: ReportExecutionStatus;
  userId?: string | null;
  startedAt?: string | null;
  finishedAt?: string | null;
  durationMillis?: number | null;
  runningTime?: string | null;
  rowCount?: number | null;
  columnCount?: number | null;
  truncated: boolean;
  parameterValues: Record<string, unknown>;
  output?: string | null;
  exceptionStackTrace?: string | null;
}

export interface ReportExecutionsResponse {
  executions: ReportExecution[];
}

export interface ResultColumn {
  name: string;
  type: ReportColumnType;
  /** False for UI-only columns that are omitted from CSV/XLSX exports. */
  exported?: boolean;
}

/** Result of an ephemeral editor "try out" run (not persisted). */
export interface ReportPreviewResponse {
  status: ReportExecutionStatus;
  columns: ResultColumn[];
  rows: ResultCell[][];
  rowCount: number;
  truncated?: boolean;
  output?: string | null;
  exceptionStackTrace?: string | null;
  runningTime?: string | null;
}

export type ResultLinkCell = { text: string; href: string };

export type ResultCell = string | number | boolean | ResultLinkCell | null;

export interface ResultPage {
  columns: ResultColumn[];
  rows: ResultCell[][];
  /** 1-based. */
  page: number;
  pageSize: number;
  totalRows: number;
  totalPages: number;
  /** -1 on the last page. */
  nextPage: number;
  /** -1 on the first page. */
  previousPage: number;
}

export interface ExportFormatsResponse {
  formats: ExportFormat[];
}
