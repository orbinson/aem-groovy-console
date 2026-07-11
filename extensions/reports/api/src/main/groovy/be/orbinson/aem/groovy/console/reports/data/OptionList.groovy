package be.orbinson.aem.groovy.console.reports.data

import groovy.json.JsonBuilder

/**
 * Options for a {@link be.orbinson.aem.groovy.console.reports.model.ReportParameterType#DYNAMIC} parameter.
 * A dynamic-options script builds one (via the <code>report.options()</code> binding) and returns it:
 *
 * <pre>
 * def opts = report.options()
 *
 * resourceResolver.findResources("SELECT * FROM [cq:Page]", "JCR-SQL2").each { page ->
 *     opts.add(page.path, page.name)   // value (submitted key), label (visible title)
 * }
 *
 * opts
 * </pre>
 *
 * The console serializes script results to a String, so this class emits a recognizable JSON envelope from
 * {@link #toString()} which the reports bundle parses back after execution (mirroring {@link ReportData}).
 */
class OptionList {

    /** Key of the JSON envelope emitted by {@link #toString()}. */
    public static final String JSON_ENVELOPE_KEY = "options"

    List<Map<String, String>> options = []

    /**
     * Add an option with a distinct value and label.
     *
     * @param value submitted value (the key)
     * @param label visible title
     */
    void add(String value, String label) {
        options.add([value: value, label: label ?: value])
    }

    /**
     * Add an option whose label is the same as its value.
     *
     * @param value submitted value, also used as the label
     */
    void add(String value) {
        add(value, value)
    }

    /**
     * Get these options as a list suitable for JSON serialization.
     *
     * @return list of <code>{value, label}</code> maps
     */
    List<Map<String, String>> toList() {
        options
    }

    @Override
    String toString() {
        new JsonBuilder([(JSON_ENVELOPE_KEY): options]).toString()
    }
}
