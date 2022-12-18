package be.orbinson.aem.groovy.console.servlets


import be.orbinson.aem.groovy.console.GroovyConsoleService
import be.orbinson.aem.groovy.console.api.impl.RequestScriptData
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.SlingHttpServletResponse
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import javax.servlet.Servlet
import javax.servlet.ServletException

@Component(service = Servlet, immediate = true, property = [
        "sling.servlet.paths=/bin/groovyconsole/save"
])
class ScriptSavingServlet extends AbstractJsonResponseServlet {

    @Reference
    private GroovyConsoleService consoleService

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws
            ServletException, IOException {
        def scriptData = new RequestScriptData(request)

        writeJsonResponse(response, consoleService.saveScript(scriptData))
    }
}