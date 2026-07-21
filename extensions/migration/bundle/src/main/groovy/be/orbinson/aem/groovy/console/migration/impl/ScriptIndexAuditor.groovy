package be.orbinson.aem.groovy.console.migration.impl

import javax.jcr.Session

/**
 * Migration-owned abstraction over the optional query-audit extension. Deliberately references none of the
 * query-audit types so that {@link DefaultMigrationService} — and therefore the whole migration extension —
 * loads and activates whether or not the query-audit bundle is installed (e.g. on AEM as a Cloud Service, where
 * it cannot be). The bridge implementation is registered only when query-audit is present.
 */
interface ScriptIndexAuditor {

    /**
     * Run {@code work}, capturing every JCR query it executes, and report each query's Oak index usage as a plain
     * map (keys: {@code statement}, {@code plan}, {@code needsIndex}).
     */
    List<Map<String, Object>> audit(Session session, Runnable work)
}
