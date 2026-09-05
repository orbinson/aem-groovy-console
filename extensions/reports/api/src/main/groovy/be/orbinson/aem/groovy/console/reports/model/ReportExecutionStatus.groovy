package be.orbinson.aem.groovy.console.reports.model

/**
 * Lifecycle status of a report execution.
 */
enum ReportExecutionStatus {

    /** Execution is in progress. */
    RUNNING,

    /** Execution completed and the result is available. */
    SUCCESS,

    /** Execution failed; see the execution's exception stack trace. */
    FAILED
}
