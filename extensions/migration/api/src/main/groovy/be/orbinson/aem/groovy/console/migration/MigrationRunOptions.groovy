package be.orbinson.aem.groovy.console.migration

import groovy.transform.TupleConstructor

/**
 * Options for triggering a migration run.
 */
@TupleConstructor
class MigrationRunOptions {

    /** Trigger that initiated the run, e.g. <code>API</code> or <code>LISTENER</code>. */
    String trigger = MigrationConstants.TRIGGER_API

    /** If true, only report pending scripts without executing them. */
    boolean dryRun

    /** Optional pre-generated run ID, used for asynchronous runs that are polled by ID. */
    String runId
}
