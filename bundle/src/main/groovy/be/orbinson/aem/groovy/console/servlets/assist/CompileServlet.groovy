package be.orbinson.aem.groovy.console.servlets.assist

import static be.orbinson.aem.groovy.console.constants.GroovyConsoleConstants.SCRIPT
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN

import be.orbinson.aem.groovy.console.api.context.impl.RequestScriptContext
import be.orbinson.aem.groovy.console.assist.AssistService
import be.orbinson.aem.groovy.console.configuration.ConfigurationService
import be.orbinson.aem.groovy.console.servlets.AbstractJsonResponseServlet
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.SlingHttpServletResponse
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import javax.servlet.Servlet
import javax.servlet.ServletException
import java.nio.charset.StandardCharsets

/**
 * Compiles (without running) the submitted script using the same shell configuration as script
 * execution, returning compilation errors as Monaco editor markers.
 */
@Component(service = Servlet, immediate = true, property = [
        "sling.servlet.paths=/bin/groovyconsole/assist/compile"
])
class CompileServlet extends AbstractJsonResponseServlet {

    @Reference
    private ConfigurationService configurationService

    @Reference
    private AssistService assistService

    @Override
    protected void doPost(SlingHttpServletRequest request,
                          SlingHttpServletResponse response) throws ServletException, IOException {
        if (!configurationService.hasPermission(request)) {
            response.status = SC_FORBIDDEN
            return
        }

        def script = request.getRequestParameter(SCRIPT)?.getString(StandardCharsets.UTF_8.name())

        if (!script) {
            response.status = SC_BAD_REQUEST
            return
        }

        def scriptContext = new RequestScriptContext(
                request: request,
                response: response,
                script: script
        )

        def markers = assistService.compile(scriptContext)

        writeJsonResponse(response, [
                ok     : markers.empty,
                markers: markers
        ])
    }
}
