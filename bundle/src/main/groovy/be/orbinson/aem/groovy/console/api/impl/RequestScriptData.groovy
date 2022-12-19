package be.orbinson.aem.groovy.console.api.impl

import be.orbinson.aem.groovy.console.api.context.ScriptData
import groovy.transform.TupleConstructor
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.resource.ResourceResolver

import static be.orbinson.aem.groovy.console.constants.GroovyConsoleConstants.*

@TupleConstructor
class RequestScriptData implements ScriptData {

    SlingHttpServletRequest request

    @Override
    ResourceResolver getResourceResolver() {
        request.resourceResolver
    }

    @Override
    String getFileName() {
        def name = request.getParameter(FILE_NAME)

        name.endsWith(EXTENSION_GROOVY) ? name : "$name$EXTENSION_GROOVY"
    }

    @Override
    String getScript() {
        request.getParameter(SCRIPT)
    }
}
