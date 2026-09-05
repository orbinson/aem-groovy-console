package be.orbinson.aem.groovy.console.reports.impl

import be.orbinson.aem.groovy.console.reports.model.ReportQueryPlan

import javax.jcr.Session

/**
 * Reports-owned abstraction over the optional query-audit extension. Deliberately references none of the
 * query-audit types so that {@link DefaultReportExecutionService} — and therefore the whole reports extension —
 * loads and activates whether or not the query-audit bundle is installed (e.g. on AEM as a Cloud Service, where
 * it cannot be). The bridge implementation ({@code QueryAuditReportIndexAuditor}) is registered only when
 * query-audit is present.
 */
interface ReportScriptIndexAuditor {

    /**
     * Run {@code work}, capturing every JCR query it executes, and report each query's Oak index usage.
     *
     * @param session session used to EXPLAIN the captured statements
     * @param work the work to run (e.g. executing the report script)
     * @return one plan per executed query, in execution order
     */
    List<ReportQueryPlan> audit(Session session, Runnable work)
}
