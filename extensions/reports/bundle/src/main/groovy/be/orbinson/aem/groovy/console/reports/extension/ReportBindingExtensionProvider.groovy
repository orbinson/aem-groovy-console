package be.orbinson.aem.groovy.console.reports.extension

import be.orbinson.aem.groovy.console.api.BindingExtensionProvider
import be.orbinson.aem.groovy.console.api.BindingVariable
import be.orbinson.aem.groovy.console.api.context.ScriptContext
import be.orbinson.aem.groovy.console.reports.context.ReportScriptContext
import be.orbinson.aem.groovy.console.reports.data.ReportDsl
import org.osgi.service.component.annotations.Component

/**
 * Injects the <code>params</code> and <code>report</code> bindings for report script executions.
 */
@Component(service = BindingExtensionProvider, immediate = true)
class ReportBindingExtensionProvider implements BindingExtensionProvider {

    @Override
    Map<String, BindingVariable> getBindingVariables(ScriptContext scriptContext) {
        def bindingVariables = [:] as Map<String, BindingVariable>

        if (scriptContext instanceof ReportScriptContext) {
            bindingVariables["params"] = new BindingVariable(scriptContext.parameterValues ?: [:], Map)
            bindingVariables["report"] = new BindingVariable(new ReportDsl(), ReportDsl)
        }

        bindingVariables
    }
}
