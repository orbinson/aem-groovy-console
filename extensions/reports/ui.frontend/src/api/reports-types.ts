/**
 * Types for the reports extension HTTP API (/bin/groovyconsole/reports*).
 * Contract: docs/reports-frontend-requirements.md
 */

export type ReportColumnType = 'STRING' | 'NUMBER' | 'DATE' | 'BOOLEAN' | 'LINK';

export type ReportParameterType = 'STRING' | 'NUMBER' | 'BOOLEAN' | 'DATE' | 'SELECT' | 'PATH' | 'TAG' | 'DYNAMIC';

/** What a browse request shows: repository nodes/pages/assets, or the cq:tags taxonomy. */
export type BrowseType = 'NODE' | 'PAGE' | 'ASSET' | 'TAG';

/** What a PATH parameter's path browser shows. */
export type PathType = 'NODE' | 'PAGE' | 'ASSET';

export interface BrowseNode {
  name: string;
  path: string;
  title?: string | null;
  primaryType?: string | null;
  hasChildren: boolean;
  selectable: boolean;
  /** For TAG browsing: the tag ID (e.g. namespace:path/to/tag) submitted as the value. */
  id?: string | null;
}

/** A resolved option for a DYNAMIC parameter. */
export interface DynamicOption {
  value: string;
  label: string;
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
  /** Whether the field accepts multiple, dynamically-added values (submitted/received as a string[]). */
  multiple?: boolean;
  options: string[];
  /** For PATH parameters: what the path browser shows. */
  pathType?: PathType;
  /** For PATH parameters: the path the browser is rooted at. For TAG parameters: the taxonomy root. */
  rootPath?: string | null;
  /** For DYNAMIC parameters: the Groovy script that produces the options. */
  optionsScript?: string | null;
  order: number;
}

/** Cron schedule for unattended report runs. */
export interface ReportSchedule {
  enabled: boolean;
  cronExpression?: string | null;
  /** Optional user to run as; blank runs as the reports service user. */
  runAs?: string | null;
  /** Set server-side to the user that saved the schedule; read-only in the UI. */
  scheduledBy?: string | null;
  /** Fixed parameter values used for scheduled runs (a scalar, or a list for a `multiple` parameter). */
  parameterValues: Record<string, ReportParameterValue>;
}

/** A configured distribution target: which distributor, which export format, and its config. */
export interface DistributionTarget {
  distributorId: string;
  format: string;
  config: Record<string, unknown>;
}

export interface Distributor {
  id: string;
  name: string;
}

export interface DistributorsResponse {
  distributors: Distributor[];
  formats: ExportFormat[];
}

/** Submitted/held value of a parameter: a scalar, or a list for a `multiple` parameter. */
export type ReportParameterValue = string | string[];

export interface ReportDefinition extends ReportSummary {
  /** Inline Groovy script. */
  script?: string | null;
  /**
   * Whether the user may edit the executable Groovy (report script + dynamic options scripts). Editing
   * metadata/parameters only needs JCR write access (canEdit); scripts additionally need console permission.
   */
  canEditScript?: boolean;
  pageSize?: number | null;
  parameters: ReportParameter[];
  schedule?: ReportSchedule | null;
  distributions: DistributionTarget[];
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
  schedule?: ReportSchedule | null;
  distributions?: DistributionTarget[];
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
  parameterValues: Record<string, unknown>;
  output?: string | null;
  exceptionStackTrace?: string | null;
  /** Distribution failures recorded after a successful run (empty when all succeeded). */
  distributionErrors?: string[] | null;
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
  output?: string | null;
  exceptionStackTrace?: string | null;
  runningTime?: string | null;
}

/** A single JCR query a report script executed, with its Oak plan. */
export interface AuditedQuery {
  statement: string;
  language?: string | null;
  plan: string;
  /** True when Oak found no covering index and fell back to a traversal. */
  needsIndex: boolean;
}

/** Result of a report "query audit" run: per-query Oak index usage (not persisted). */
export interface ReportQueryAuditResponse {
  status: ReportExecutionStatus;
  queries: AuditedQuery[];
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
