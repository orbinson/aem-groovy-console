import type { AuditRecord } from './api/types';

export interface ActiveJobInfo {
  id: string;
  title?: string;
  description?: string;
  script?: string;
  startTime?: string;
}

export interface GcConfig {
  contextPath: string;
  aem: boolean;
  hasScheduledJobPermission: boolean;
  auditEnabled: boolean;
  distributedExecutionEnabled: boolean;
  emailEnabled: boolean;
  activeJobs: ActiveJobInfo[];
  classicUrl: string;
  groovyVersion: string;
  auditRecord: AuditRecord | null;
  /** ES module URLs announced by ConsoleUiExtensionProvider services (UI extensions). */
  uiExtensions: string[];
}

declare global {
  interface Window {
    __GC_CONFIG__?: GcConfig;
  }
}

// Dev fallback: the HTL entry page injects window.__GC_CONFIG__ in production; the Vite
// dev server has no server-side model, so default to a permissive Sling-flavoured config.
const devDefaults: GcConfig = {
  contextPath: '',
  aem: new URLSearchParams(window.location.search).get('aem') === 'true',
  hasScheduledJobPermission: true,
  auditEnabled: true,
  distributedExecutionEnabled: false,
  emailEnabled: false,
  activeJobs: [],
  classicUrl: '/apps/groovyconsole.classic.html',
  groovyVersion: '',
  auditRecord: null,
  uiExtensions: [],
};

export const config: GcConfig = { ...devDefaults, ...(window.__GC_CONFIG__ ?? {}) };
