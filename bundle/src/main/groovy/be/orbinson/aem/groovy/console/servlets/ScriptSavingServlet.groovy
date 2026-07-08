package be.orbinson.aem.groovy.console.servlets


import be.orbinson.aem.groovy.console.GroovyConsoleService
import be.orbinson.aem.groovy.console.api.context.impl.RequestScriptData
import be.orbinson.aem.groovy.console.configuration.ConfigurationService
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.SlingHttpServletResponse
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import javax.servlet.Servlet
import javax.servlet.ServletException

import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN

@Component(service = Servlet, immediate = true, property = [
        "sling.servlet.paths=/bin/groovyconsole/save"
])
class ScriptSavingServlet extends AbstractJsonResponseServlet {

    @Reference
    private GroovyConsoleService consoleService

    @Reference
    private ConfigurationService configurationService

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws
            ServletException, IOException {
        if (!configurationService.hasPermission(request)) {
            response.status = SC_FORBIDDEN

            return
        }

        def scriptData = new RequestScriptData(request)

        writeJsonResponse(response, consoleService.saveScript(scriptData))
    }
}
