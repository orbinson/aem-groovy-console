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

    /**
     * Optional path to scope the run to, instead of the configured scripts base path: either a folder to
     * recurse into (same alphanumeric ordering and run-mode filtering as a full run), or a single script
     * to run directly. Still subject to the same checksum-based run-once and fail-fast semantics as a full
     * run. Mirrors {@code AecuService.execute(String path, String data)}.
     */
    String path

    /**
     * Optional JSON or String data made available to every script executed in this run as the "data"
     * binding variable (see {@code be.orbinson.aem.groovy.console.api.context.ScriptContext#getData()}).
     * Mirrors {@code AecuService.execute(String path, String data)}.
     */
    String data

    /**
     * If true, measure the Oak index usage of the queries each migration script runs (requires the optional
     * query-audit extension to be installed; otherwise it is a no-op). Intended for CI validation.
     */
    boolean measureIndexUsage
}
