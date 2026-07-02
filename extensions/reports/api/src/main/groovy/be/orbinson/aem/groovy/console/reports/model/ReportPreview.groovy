package be.orbinson.aem.groovy.console.reports.model

import be.orbinson.aem.groovy.console.reports.data.ReportData
import groovy.transform.ToString

/**
 * Result of an ephemeral report "try out" run (see {@code ReportExecutionService.preview}).  Unlike a normal
 * execution it is not persisted: the typed result, captured output and any failure are returned directly for
 * the report editor's inline preview.
 */
@ToString(includePackage = false, includeNames = true)
class ReportPreview {

    /** SUCCESS or FAILED. */
    ReportExecutionStatus status

    /** Typed result (columns + rows); null when the run failed. */
    ReportData data

    /** Captured script output (println etc.). */
    String output

    /** Stack trace when {@link #status} is FAILED. */
    String exceptionStackTrace

    /** Human-readable running time. */
    String runningTime
}
