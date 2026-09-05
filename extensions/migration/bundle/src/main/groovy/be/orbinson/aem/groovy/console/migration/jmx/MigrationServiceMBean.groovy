package be.orbinson.aem.groovy.console.migration.jmx

import org.osgi.annotation.versioning.ProviderType

/**
 * JMX interface for triggering and inspecting migration runs, e.g. from JConsole or a scripted JMX client.
 * Mirrors {@code de.valtech.aecu.core.jmx.AecuServiceMBean}, adapted to this service's run-once/fail-fast model
 * (there is no "bypass history" execution mode: every run, however triggered, respects the same checksum-based
 * run-once and fail-fast semantics as an HTTP-triggered run).
 * <p>
 * Registered as a plain "Standard MBean" (this interface's simple name + the "MBean" suffix, per the JMX naming
 * convention) rather than via an annotated/dynamic MBean base class, so this stays dependency-free and works the
 * same on plain Sling and on AEM.
 */
@ProviderType
interface MigrationServiceMBean {

    /**
     * Returns true if a migration run is currently in progress.
     */
    boolean isRunning()

    /**
     * Returns the paths of all scripts that are currently pending (new, changed or previously failed).
     */
    List<String> getPendingScripts()

    /**
     * Runs all pending migration scripts synchronously and returns a summary of the result.
     */
    String run()

    /**
     * Runs the pending scripts below the given path (a single script or a folder) instead of the configured
     * scripts base path, and returns a summary of the result.
     *
     * @param path path to a single script or folder to run
     */
    String run(String path)

    /**
     * Same as {@link #run(String)}, and makes the given JSON or plain string data available to every script in
     * the run as the "data" binding variable.
     *
     * @param path path to a single script or folder to run
     * @param data JSON or plain string data, bound as "data" in every script
     */
    String run(String path, String data)

    /**
     * Returns a summary of the most recent migration runs, newest first.
     *
     * @param count maximum number of runs to include
     */
    String getRuns(int count)

}
