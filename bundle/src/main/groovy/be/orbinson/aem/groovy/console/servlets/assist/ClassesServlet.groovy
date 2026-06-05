package be.orbinson.aem.groovy.console.servlets.assist

import be.orbinson.aem.groovy.console.assist.AssistService
import be.orbinson.aem.groovy.console.configuration.ConfigurationService
import be.orbinson.aem.groovy.console.servlets.AbstractJsonResponseServlet
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.SlingHttpServletResponse
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import javax.servlet.Servlet
import javax.servlet.ServletException

import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN

@Component(service = Servlet, immediate = true, property = [
        "sling.servlet.paths=/bin/groovyconsole/assist/classes"
])
class ClassesServlet extends AbstractJsonResponseServlet {

    private static final int DEFAULT_LIMIT = 1000

    private static final int MAX_LIMIT = 2000

    @Reference
    private ConfigurationService configurationService

    @Reference
    private AssistService assistService

    @Override
    protected void doGet(SlingHttpServletRequest request,
                         SlingHttpServletResponse response) throws ServletException, IOException {
        if (!configurationService.hasPermission(request)) {
            response.status = SC_FORBIDDEN
            return
        }

        def prefix = request.getParameter("prefix") ?: ""
        def limit = Math.min((request.getParameter("limit") ?: "$DEFAULT_LIMIT") as int, MAX_LIMIT)

        writeJsonResponse(response, assistService.findClasses(prefix, limit))
    }
}
