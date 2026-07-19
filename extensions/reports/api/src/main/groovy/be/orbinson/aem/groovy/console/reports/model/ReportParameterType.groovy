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
    PATH,

    /**
     * AEM tag picker, passed as a String tag ID (e.g. <code>namespace:path/to/tag</code>).  Browses
     * <code>cq:Tag</code> nodes under the configured taxonomy root purely via JCR, so it degrades to an empty
     * picker (rather than failing) on a plain Sling instance where no tag tree exists.
     */
    TAG,

    /**
     * Dropdown whose options are produced at runtime by an author-supplied Groovy script.  The script returns
     * an {@link be.orbinson.aem.groovy.console.reports.data.OptionList} (via the <code>report.options()</code>
     * binding) of value/label pairs; the submitted value is the selected option's value.
     */
    DYNAMIC
}
