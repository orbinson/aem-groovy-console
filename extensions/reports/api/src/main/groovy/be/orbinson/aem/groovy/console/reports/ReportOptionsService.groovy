package be.orbinson.aem.groovy.console.reports

import org.apache.sling.api.resource.ResourceResolver
import org.osgi.annotation.versioning.ProviderType

/**
 * Resolves the options of a {@link be.orbinson.aem.groovy.console.reports.model.ReportParameterType#DYNAMIC}
 * parameter by running its author-supplied Groovy script.  The script runs through the same
 * {@code ReportScriptContext} as a report (so the <code>report</code> and <code>params</code> bindings behave
 * as in a real run) using the requesting user's resolver, and is expected to return an
 * {@link be.orbinson.aem.groovy.console.reports.data.OptionList}.
 */
@ProviderType
interface ReportOptionsService {

    /**
     * Run a dynamic-options script and return the resolved options.
     *
     * @param script the Groovy options script
     * @param parameterValues values of parameters already entered (bound as <code>params</code> so options can
     *        depend on earlier fields); each value is a String or a List of Strings
     * @param resourceResolver resolver of the requesting user; the script runs with a detached clone of it
     * @return the resolved options, each a <code>{value, label}</code> map
     * @throws ReportException when the options script fails
     */
    List<Map<String, String>> resolveOptions(String script, Map<String, Object> parameterValues,
                                              ResourceResolver resourceResolver)
}
