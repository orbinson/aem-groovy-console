package be.orbinson.aem.groovy.console.reports.model

import groovy.transform.ToString

/**
 * Result of a report "query audit" run (see {@code ReportExecutionService.auditQueries}). Like a preview it runs the
 * (possibly unsaved) script once on a detached resolver and is not persisted, but instead of the typed result it
 * reports, per JCR query the script executed, whether the live Oak repository has a covering index. Lets report
 * admins check that a report — effectively an often-run query — is index-backed.
 */
@ToString(includePackage = false, includeNames = true)
class ReportQueryAudit {

    /** SUCCESS or FAILED (the underlying script run). */
    ReportExecutionStatus status

    /** One entry per distinct query the script executed; empty when the script runs no queries. */
    List<ReportQueryPlan> queries = []

    /** Captured script output (println etc.). */
    String output

    /** Stack trace when {@link #status} is FAILED. */
    String exceptionStackTrace

    /** Human-readable running time. */
    String runningTime
}
