package be.orbinson.aem.groovy.console.migration

import org.osgi.annotation.versioning.ConsumerType

/**
 * Latest known registry state for a migration script.
 */
@ConsumerType
interface MigrationScriptState {

    String getScriptPath()

    /**
     * Get the SHA-256 checksum of the script content as last executed, or the current content checksum if the script
     * has never been executed.
     *
     * @return hex-encoded checksum
     */
    String getChecksum()

    /**
     * Get the status of the last execution, or null if the script has never been executed.
     *
     * @return last execution status
     */
    MigrationStatus getStatus()

    Calendar getLastRunDate()

    String getRunningTime()

    /**
     * Check if the script is flagged to run on every trigger via the <code>.always.groovy</code> file name suffix.
     *
     * @return true if the script always runs
     */
    boolean isAlways()

    /**
     * Check if the script will be executed on the next migration trigger, i.e. it has never run, its content changed
     * or its last execution was not successful.
     *
     * @return true if the script is pending execution
     */
    boolean isPending()
}
