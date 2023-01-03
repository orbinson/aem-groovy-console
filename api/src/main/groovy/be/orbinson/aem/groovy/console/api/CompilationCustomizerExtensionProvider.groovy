package be.orbinson.aem.groovy.console.api

import org.codehaus.groovy.control.customizers.CompilationCustomizer
import org.osgi.annotation.versioning.ConsumerType

/**
 * Services may implement this interface to customize the compiler configuration for Groovy script execution.
 */
@ConsumerType
interface CompilationCustomizerExtensionProvider {

    /**
     * Get a list of compilation customizers for Groovy script execution.
     *
     * @return list of compilation customizers
     */
    List<CompilationCustomizer> getCompilationCustomizers()
}
