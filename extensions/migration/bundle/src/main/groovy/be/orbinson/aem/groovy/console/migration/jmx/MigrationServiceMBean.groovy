package be.orbinson.aem.groovy.console.migration.jmx

import com.adobe.granite.jmx.annotation.Description
import com.adobe.granite.jmx.annotation.Name
import org.osgi.annotation.versioning.ProviderType

/**
 * JMX interface for triggering and inspecting migration runs, e.g. from JConsole or a scripted JMX client.
 * Mirrors {@code de.valtech.aecu.core.jmx.AecuServiceMBean}, adapted to this service's run-once/fail-fast model
 * (there is no "bypass history" execution mode: every run, however triggered, respects the same checksum-based
 * run-once and fail-fast semantics as an HTTP-triggered run).
 */
@Description("AEM Groovy Console - Migration Extension")
@ProviderType
interface MigrationServiceMBean {

    @Description("Returns true if a migration run is currently in progress")
    boolean isRunning()

    @Description("Returns the paths of all scripts that are currently pending (new, changed or previously failed)")
    List<String> getPendingScripts()

    @Description("Runs all pending migration scripts synchronously and returns a summary of the result")
    String run()

    @Description("Runs the pending scripts below the given path (a single script or a folder) instead of the " +
            "configured scripts base path, and returns a summary of the result")
    String run(@Name("Path") @Description("Path to a single script or folder to run") String path)

    @Description("Same as run(Path), and makes the given JSON or plain string data available to every script in " +
            "the run as the \"data\" binding variable")
    String run(@Name("Path") @Description("Path to a single script or folder to run") String path,
               @Name("Data") @Description("JSON or plain string data, bound as \"data\" in every script") String data)

    @Description("Returns a summary of the most recent migration runs, newest first")
    String getRuns(@Name("Count") @Description("Maximum number of runs to include") int count)

}
