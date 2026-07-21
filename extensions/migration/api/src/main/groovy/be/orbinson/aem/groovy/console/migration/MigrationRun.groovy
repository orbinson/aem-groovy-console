package be.orbinson.aem.groovy.console.migration

import org.osgi.annotation.versioning.ConsumerType

/**
 * Aggregate result of a migration run.
 */
@ConsumerType
interface MigrationRun {

    String getRunId()

    MigrationStatus getStatus()

    /**
     * Get the trigger that initiated this run, e.g. <code>API</code> or <code>LISTENER</code>.
     *
     * @return trigger name
     */
    String getTrigger()

    Calendar getStartDate()

    Calendar getEndDate()

    String getRunningTime()

    /**
     * Get the run-level error message, set when an asynchronous run failed outside of script execution
     * (e.g. the migration job could not be processed).  Script errors are reported per result instead.
     *
     * @return error message or an empty string
     */
    String getError()

    /**
     * Get the path this run was scoped to, or an empty string when the configured scripts base path was
     * used in full.
     *
     * @return scoped path, or an empty string
     */
    String getPath()

    /**
     * Get the per-script results for this run, in execution order.
     *
     * @return list of script results
     */
    List<MigrationScriptResult> getResults()
}
