package be.orbinson.aem.groovy.console.extension

import be.orbinson.aem.groovy.console.api.BindingExtensionProvider
import be.orbinson.aem.groovy.console.api.CompilationCustomizerExtensionProvider
import be.orbinson.aem.groovy.console.api.StarImportExtensionProvider
import be.orbinson.aem.groovy.console.api.context.ScriptContext
import org.osgi.annotation.versioning.ProviderType

/**
 * Service that dynamically binds extensions providing additional script bindings, star imports, and script metaclasses.
 * The extension service is also responsible for aggregating all metaclass extension providers and handling the
 * registration and removal of metaclass closures as these provider implementations are added or removed from the
 * OSGi container.
 */
@ProviderType
interface ExtensionService extends BindingExtensionProvider, CompilationCustomizerExtensionProvider,
        StarImportExtensionProvider {

    /**
     * Get a list of all script metaclass closures for bound extensions.
     *
     * @param scriptContext current script execution context
     * @return list of metaclass closures
     */
    List<Closure> getScriptMetaClasses(ScriptContext scriptContext)

    /**
     * Get the set of all classes that have associated metaclasses.  This value may change as metaclass extension
     * providers are added or removed from the OSGi container.
     *
     * @return set of classes that have associated metaclasses registered in the container
     */
    Set<Class> getMetaClasses()
}
