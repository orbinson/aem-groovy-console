package be.orbinson.aem.groovy.console.migration

import org.apache.sling.api.SlingHttpServletRequest
import org.osgi.annotation.versioning.ProviderType

/**
 * Service for executing deployment migration scripts with run-once semantics.
 *
 * <p>Migration scripts are Groovy scripts deployed (typically via content package) below a configurable base path.
 * A light registry tracks the content checksum and last status per script; a script is executed when it is new, its
 * content changed or its last execution was not successful.  Scripts execute in deterministic alphanumeric path order
 * and a run stops at the first failure (fail-fast).</p>
 */
@ProviderType
interface MigrationService {

    /**
     * Run all pending migration scripts synchronously.
     *
     * @param options run options
     * @return aggregate run result
     * @throws IllegalStateException if a migration run is already in progress
     */
    MigrationRun run(MigrationRunOptions options)

    /**
     * Enqueue an asynchronous migration run as a Sling job.
     *
     * @param options run options
     * @return run ID that can be used to poll the run status
     * @throws IllegalStateException if a migration run is already in progress
     */
    String enqueue(MigrationRunOptions options)

    /**
     * Get a migration run by its ID.
     *
     * @param runId run ID
     * @return run or null if not found
     */
    MigrationRun getRun(String runId)

    /**
     * Get the recent migration runs, newest first.
     *
     * @return list of runs
     */
    List<MigrationRun> getRuns()

    /**
     * Get the registry state for all migration scripts currently below the configured base path.
     *
     * @return list of script states
     */
    List<MigrationScriptState> getRegistry()

    /**
     * Get the paths of all scripts that would be executed on the next trigger, in execution order.
     *
     * @return list of pending script paths
     */
    List<String> getPendingScripts()

    /**
     * Check if a migration run is currently in progress.
     *
     * @return true if a run is in progress
     */
    boolean isRunning()

    /**
     * Check if the request user is authorized to trigger migration runs.
     *
     * @param request servlet request
     * @return true if the user is an admin or member of an allowed migration group
     */
    boolean hasPermission(SlingHttpServletRequest request)
}
