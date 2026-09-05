package be.orbinson.aem.groovy.console.impl

import be.orbinson.aem.groovy.console.api.context.ScriptContext
import be.orbinson.aem.groovy.console.configuration.ConfigurationService
import be.orbinson.aem.groovy.console.extension.ExtensionService
import groovy.transform.TimedInterrupt
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer
import org.codehaus.groovy.control.customizers.CompilationCustomizer
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * Builds the binding and compiler configuration used for console script executions.  Shared between script
 * execution (DefaultGroovyConsoleService) and compile-only diagnostics (DefaultAssistService) so that
 * compilation behavior can never drift between the two.
 */
@Component(service = GroovyShellFactory)
class GroovyShellFactory {

    @Reference
    private ConfigurationService configurationService

    @Reference
    private ExtensionService extensionService

    Binding createBinding(ScriptContext scriptContext) {
        def binding = new Binding()

        extensionService.getBindingVariables(scriptContext).each { name, variable ->
            binding.setVariable(name, variable.value)
        }

        binding
    }

    CompilerConfiguration createConfiguration() {
        def configuration = new CompilerConfiguration()

        if (configurationService.threadTimeout > 0) {
            // add timed interrupt using configured timeout value
            configuration.addCompilationCustomizers(new ASTTransformationCustomizer(value: configurationService.threadTimeout, TimedInterrupt))
        }

        configuration.addCompilationCustomizers(extensionService.compilationCustomizers
                as CompilationCustomizer[])
    }
}
