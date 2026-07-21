package be.orbinson.aem.groovy.console.reports

/**
 * Exception thrown for report persistence or execution errors.
 */
class ReportException extends RuntimeException {

    ReportException(String message) {
        super(message)
    }

    ReportException(String message, Throwable cause) {
        super(message, cause)
    }
}
