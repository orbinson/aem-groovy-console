package be.orbinson.aem.groovy.console.reports.data

/**
 * Column types for report data.  Types drive cell rendering in the reports UI and typed cells in exports
 * (e.g. numeric/date cells in XLSX).
 */
enum ReportColumnType {

    /** Plain text. */
    STRING,

    /** Numeric value, stored as a JSON number. */
    NUMBER,

    /** Date/time value, stored as an ISO-8601 formatted string (UTC). */
    DATE,

    /** Boolean value. */
    BOOLEAN,

    /** Hyperlink, stored as a map with <code>text</code> and <code>href</code> keys. */
    LINK
}
