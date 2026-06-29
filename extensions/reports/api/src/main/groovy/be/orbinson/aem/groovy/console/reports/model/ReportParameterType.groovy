package be.orbinson.aem.groovy.console.reports.model

/**
 * Types for report parameters.  Types drive the input rendered in the reports UI and the coercion applied to
 * submitted values before they are passed to the report script via the <code>params</code> binding.
 */
enum ReportParameterType {

    /** Plain text input, passed as a String. */
    STRING,

    /** Numeric input, passed as a BigDecimal. */
    NUMBER,

    /** Checkbox input, passed as a Boolean. */
    BOOLEAN,

    /** Date input (ISO-8601, e.g. <code>2026-06-04</code> or <code>2026-06-04T10:15:30Z</code>), passed as a Date. */
    DATE,

    /** Dropdown input restricted to the configured options, passed as a String. */
    SELECT,

    /** Repository path input, passed as a String. */
    PATH
}
