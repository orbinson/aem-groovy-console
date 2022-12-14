package org.cid15.aem.groovy.console.servlets

import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.SlingHttpServletResponse
import org.cid15.aem.groovy.console.configuration.ConfigurationService
import org.jetbrains.annotations.NotNull
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import javax.servlet.Servlet
import javax.servlet.ServletException

import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN

@Component(service = Servlet, immediate = true, property = [
        "sling.servlet.paths=/bin/groovyconsole/replicate"
])
class ReplicateScriptServlet extends AbstractJsonResponseServlet {
    @Reference
    private ConfigurationService configurationService

    @Override
    protected void doPost(@NotNull SlingHttpServletRequest request, @NotNull SlingHttpServletResponse response) throws ServletException, IOException {
        if (configurationService.hasPermission(request)) {
            // TODO: read script, create node, replicate script, cleanup
        } else {
            response.status = SC_FORBIDDEN
        }
    }
}
