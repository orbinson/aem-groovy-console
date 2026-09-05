package be.orbinson.aem.groovy.console.reports.context

import be.orbinson.aem.groovy.console.api.context.ScriptContext
import org.osgi.annotation.versioning.ProviderType

/**
 * Script context for report executions.  Extension providers can check <code>instanceof</code> against this
 * type to contribute bindings or behavior only for report script executions.
 */
@ProviderType
interface ReportScriptContext extends ScriptContext {

    /**
     * Name of the report being executed.
     *
     * @return report name
     */
    String getReportName()

    /**
     * Coerced parameter values for this execution, exposed to the script as the <code>params</code> binding.
     *
     * @return parameter values
     */
    Map<String, Object> getParameterValues()
}
