export interface RunScriptResponse {
  date?: string;
  script?: string;
  data?: string;
  result?: string;
  output?: string;
  exceptionStackTrace?: string;
  runningTime?: string;
  userId?: string;
  jobId?: string;
  mediaType?: string;
  outputFileName?: string;
  message?: string;
}

export interface AuditRecord {
  date: string;
  jobTitle?: string;
  scriptPreview?: string;
  userId: string;
  script: string;
  data?: string;
  result?: string;
  output?: string;
  exception?: string;
  exceptionStackTrace?: string;
  runningTime?: string;
  queryString?: string;
  relativePath: string;
  downloadUrl?: string;
}

export interface ScheduledJob {
  jobTitle: string;
  jobDescription?: string;
  script: string;
  scriptPreview?: string;
  data?: string;
  cronExpression?: string;
  nextExecutionDate?: string;
  downloadUrl?: string;
  scheduledJobId: string;
  mediaType?: string;
  emailTo?: string;
}

export interface ScheduleJobRequest {
  script: string;
  data?: string;
  jobTitle: string;
  jobDescription?: string;
  cronExpression?: string;
  mediaType?: string;
  emailTo?: string;
  scheduledJobId?: string;
}

export type ServicesMap = Record<string, string>;

export interface TableResult {
  columns: string[];
  rows: unknown[][];
}

export interface ClassSuggestion {
  fqcn: string;
  name: string;
  package: string;
  exported: boolean;
}

export interface ClassDictionaryResponse {
  truncated: boolean;
  classes: ClassSuggestion[];
}

export interface ClassMember {
  kind: 'method' | 'field' | 'property';
  name: string;
  returnType?: string;
  type?: string;
  params?: string[];
  static: boolean;
  source: 'java' | 'groovy';
}

export interface ClassMembersResponse {
  fqcn: string;
  members: ClassMember[];
  error?: string;
}

export interface BindingInfo {
  name: string;
  type: string;
  link?: string;
}

export interface StarImportInfo {
  packageName: string;
  link?: string;
}

export interface MetaClassInfo {
  type: string;
  methods: string[];
}

export interface AssistContextResponse {
  bindings: BindingInfo[];
  starImports: StarImportInfo[];
  metaClasses: MetaClassInfo[];
}

export interface CompileMarker {
  severity: 'error' | 'warning';
  message: string;
  startLineNumber: number;
  startColumn: number;
  endLineNumber: number;
  endColumn: number;
}

export interface CompileResponse {
  ok: boolean;
  markers: CompileMarker[];
}
