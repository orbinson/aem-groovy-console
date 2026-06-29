package be.orbinson.aem.groovy.console.reports.model

import groovy.transform.ToString

/**
 * Metadata of a single report execution, persisted under <code>/var/groovyconsole/reports/executions</code>.
 * The tabular result itself is read via the result store.
 */
@ToString(includePackage = false, includeNames = true, excludes = "output,exceptionStackTrace")
class ReportExecution {

    /** Execution identifier (path relative to the executions root). */
    String id

    /** Name of the executed report. */
    String reportName

    ReportExecutionStatus status

    /** User that ran the report. */
    String userId

    Calendar startedAt

    Calendar finishedAt

    Long durationMillis

    /** Running time as reported by the console (HH:mm:ss.SSS). */
    String runningTime

    Long rowCount

    Long columnCount

    /** Whether the result was truncated because it exceeded the configured maximum row count. */
    boolean truncated

    /** Coerced parameter values used for this execution. */
    Map<String, Object> parameterValues = [:]

    /** Captured script output (possibly truncated). */
    String output

    String exceptionStackTrace
}
