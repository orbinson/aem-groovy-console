package be.orbinson.aem.groovy.console.api

import be.orbinson.aem.groovy.console.api.context.ScriptContext
import org.osgi.annotation.versioning.ConsumerType

/**
 * Services may implement this interface to supply additional metamethods to apply to the <code>Script</code> metaclass.
 */
@ConsumerType
interface ScriptMetaClassExtensionProvider {

    /**
     * Get a closure to register a metaclass for the script to be executed.
     *
     * @param scriptContext current script execution context
     * @return a closure containing metamethods to register for scripts
     */
    Closure getScriptMetaClass(ScriptContext scriptContext)
}
