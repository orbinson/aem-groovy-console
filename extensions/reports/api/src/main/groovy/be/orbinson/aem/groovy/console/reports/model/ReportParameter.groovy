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

    /** Allowed values for {@link ReportParameterType#SELECT} parameters. */
    List<String> options = []

    /**
     * For {@link ReportParameterType#PATH} parameters: what the path browser shows.
     * One of <code>NODE</code> (any JCR node), <code>PAGE</code> (AEM pages) or <code>ASSET</code> (DAM assets).
     */
    String pathType = "NODE"

    /** For {@link ReportParameterType#PATH} parameters: the path the browser is rooted at (optional). */
    String rootPath

    /** Display order in the reports UI. */
    int order
}
