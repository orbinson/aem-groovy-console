package be.orbinson.aem.groovy.console.extension

import be.orbinson.aem.groovy.console.api.BindingExtensionProvider
import be.orbinson.aem.groovy.console.api.CompilationCustomizerExtensionProvider
import be.orbinson.aem.groovy.console.api.StarImportExtensionProvider
import be.orbinson.aem.groovy.console.api.context.ScriptContext

/**
 * Service that dynamically binds extensions providing additional script bindings, star imports, and script metaclasses.
 */
interface ExtensionService extends BindingExtensionProvider, CompilationCustomizerExtensionProvider,
        StarImportExtensionProvider {

    /**
     * Get a list of all script metaclass closures for bound extensions.
     *
     * @param scriptContext current script execution context
     * @return list of metaclass closures
     */
    List<Closure> getScriptMetaClasses(ScriptContext scriptContext)
}