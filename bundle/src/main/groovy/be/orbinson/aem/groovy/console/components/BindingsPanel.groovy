package be.orbinson.aem.groovy.console.components

import be.orbinson.aem.groovy.console.api.BindingVariable
import be.orbinson.aem.groovy.console.api.context.impl.RequestScriptContext
import be.orbinson.aem.groovy.console.extension.ExtensionService
import groovy.transform.Memoized
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.SlingHttpServletResponse
import org.apache.sling.models.annotations.Model
import org.apache.sling.models.annotations.injectorspecific.OSGiService
import org.apache.sling.models.annotations.injectorspecific.ScriptVariable
import org.apache.sling.models.annotations.injectorspecific.Self

@Model(adaptables = SlingHttpServletRequest)
class BindingsPanel {

    @Self
    private SlingHttpServletRequest request

    @ScriptVariable
    private SlingHttpServletResponse response

    @OSGiService
    private ExtensionService extensionService

    @Memoized
    Map<String, BindingVariable> getBindingVariables() {
        def scriptContext = new RequestScriptContext(
                request: request,
                response: response
        )

        extensionService.getBindingVariables(scriptContext)
    }
}
