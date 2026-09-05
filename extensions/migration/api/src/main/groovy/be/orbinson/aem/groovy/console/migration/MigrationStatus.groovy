package be.orbinson.aem.groovy.console.migration

/**
 * Status of a migration run or an individual migration script execution.
 */
enum MigrationStatus {

    /** Script or run completed successfully. */
    SUCCESS,

    /** Script or run failed. */
    FAILED,

    /** Script was not executed because a preceding script failed (fail-fast). */
    SKIPPED,

    /** Run is currently in progress. */
    RUNNING,

    /** Script is pending execution (used for dry runs). */
    PENDING
}
