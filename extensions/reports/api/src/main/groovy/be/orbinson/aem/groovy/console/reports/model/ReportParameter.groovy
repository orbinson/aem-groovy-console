package be.orbinson.aem.groovy.console.reports.model

import groovy.transform.ToString

/**
 * Parameter declared on a report definition.
 */
@ToString(includePackage = false, includeNames = true)
class ReportParameter {

    /** Parameter name, used as the key in the <code>params</code> script binding. */
    String name

    /** Display label in the reports UI. */
    String label

    /** Parameter type. */
    ReportParameterType type = ReportParameterType.STRING

    /** Default value applied when no value is submitted. */
    String defaultValue

    /** Whether a value is required to run the report. */
    boolean required

    /**
     * Whether the field accepts multiple, dynamically-added values.  When true the value submitted for this
     * parameter is a list and the report script receives <code>params.&lt;name&gt;</code> as a
     * {@link java.util.List} of coerced values.
     */
    boolean multiple

    /** Allowed values for {@link ReportParameterType#SELECT} parameters. */
    List<String> options = []

    /**
     * For {@link ReportParameterType#PATH} parameters: what the path browser shows.
     * One of <code>NODE</code> (any JCR node), <code>PAGE</code> (AEM pages) or <code>ASSET</code> (DAM assets).
     */
    String pathType = "NODE"

    /**
     * For {@link ReportParameterType#PATH} parameters: the path the browser is rooted at (optional).
     * For {@link ReportParameterType#TAG} parameters: the taxonomy root the tag browser is rooted at
     * (defaults to <code>/content/cq:tags</code>).
     */
    String rootPath

    /**
     * For {@link ReportParameterType#DYNAMIC} parameters: the Groovy script that produces the options.
     * Persisted as a <code>.groovy</code> <code>nt:file</code> subnode of the parameter node; this field is the
     * in-memory carrier used over the HTTP API.
     */
    String optionsScript

    /** Display order in the reports UI. */
    int order
}
