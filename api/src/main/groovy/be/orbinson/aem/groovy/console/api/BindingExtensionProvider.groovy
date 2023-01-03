package be.orbinson.aem.groovy.console.api

import be.orbinson.aem.groovy.console.api.context.ScriptContext
import org.osgi.annotation.versioning.ConsumerType

/**
 * Services may implement this interface to supply additional binding values for Groovy script executions.
 */
@ConsumerType
interface BindingExtensionProvider {

    /**
     * Get the binding variables for this script execution.  All bindings provided by extension services will be merged
     * prior to script execution.
     *
     * @param scriptContext context for current script execution
     * @return map of binding variables for request
     */
    Map<String, BindingVariable> getBindingVariables(ScriptContext scriptContext)
}
