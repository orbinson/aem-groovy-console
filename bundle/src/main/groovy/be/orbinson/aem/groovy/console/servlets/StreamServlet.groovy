package be.orbinson.aem.groovy.console.servlets

import be.orbinson.aem.groovy.console.configuration.ConfigurationService
import be.orbinson.aem.groovy.console.streaming.ExecutionRegistry
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.SlingHttpServletResponse
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import javax.servlet.Servlet
import javax.servlet.ServletException

import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND

/**
 * Polling endpoint for asynchronous (streaming) script executions started via
 * POST /bin/groovyconsole/post with async=true.
 */
@Component(service = Servlet, immediate = true, property = [
        "sling.servlet.paths=/bin/groovyconsole/stream"
])
class StreamServlet extends AbstractJsonResponseServlet {

    @Reference
    private ConfigurationService configurationService

    @Reference
    private ExecutionRegistry executionRegistry

    @Override
    protected void doGet(SlingHttpServletRequest request,
                         SlingHttpServletResponse response) throws ServletException, IOException {
        if (!configurationService.hasPermission(request)) {
            response.status = SC_FORBIDDEN
            return
        }

        // polling responses must never be cached (e.g. by the AEMaaCS CDN)
        response.setHeader("Cache-Control", "no-store")

        def executionId = request.getParameter("executionId")
        def offset = (request.getParameter("offset") ?: "0") as int

        def result = executionId ? executionRegistry.poll(executionId, offset) : null

        if (result == null) {
            response.status = SC_NOT_FOUND
            return
        }

        writeJsonResponse(response, result)
    }
}
