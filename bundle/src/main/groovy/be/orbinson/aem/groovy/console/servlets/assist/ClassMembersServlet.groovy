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

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN

@Component(service = Servlet, immediate = true, property = [
        "sling.servlet.paths=/bin/groovyconsole/assist/members"
])
class ClassMembersServlet extends AbstractJsonResponseServlet {

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

        def className = request.getParameter("class")

        if (!className) {
            response.status = SC_BAD_REQUEST
            return
        }

        writeJsonResponse(response, assistService.getMembers(className))
    }
}
