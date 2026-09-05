package be.orbinson.aem.groovy.console.migration.impl

/**
 * Thrown when an asynchronous migration run could not be enqueued as a Sling job.  Distinct from
 * {@link IllegalStateException} (run already in progress) so callers can map it to a server error
 * instead of a conflict.
 */
class MigrationJobException extends RuntimeException {

    MigrationJobException(String message) {
        super(message)
    }
}
