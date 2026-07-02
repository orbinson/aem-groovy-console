package be.orbinson.aem.groovy.console.migration

import org.osgi.annotation.versioning.ConsumerType

/**
 * Result of a single migration script execution within a migration run.
 */
@ConsumerType
interface MigrationScriptResult {

    String getScriptPath()

    /**
     * Get the SHA-256 checksum of the script content at the time of execution.
     *
     * @return hex-encoded checksum
     */
    String getChecksum()

    MigrationStatus getStatus()

    String getRunningTime()

    long getDurationMillis()

    /**
     * Get the script output, truncated to the configured maximum length.  Full output is available in the regular
     * audit history.
     *
     * @return truncated script output
     */
    String getOutput()

    String getError()
}
