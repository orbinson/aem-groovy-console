package be.orbinson.aem.groovy.console.reports.servlets

import be.orbinson.aem.groovy.console.reports.impl.ReportsConfigurationService
import groovy.util.logging.Slf4j
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.SlingHttpServletResponse
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import javax.servlet.Servlet
import javax.servlet.ServletException

/**
 * Global reports feature flags (OSGi), so the UI can hide the scheduling / distribution sections when an
 * operator has turned those features off.
 *
 * <code>GET /bin/groovyconsole/reports/config.json</code>
 */
@Component(service = Servlet, immediate = true, property = [
        "sling.servlet.paths=/bin/groovyconsole/reports/config"
])
@Slf4j("LOG")
class ReportsConfigServlet extends AbstractReportsServlet {

    @Reference
    private ReportsConfigurationService reportsConfigurationService

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        writeJsonResponse(response, [
                schedulingEnabled  : reportsConfigurationService.schedulingEnabled,
                distributionEnabled: reportsConfigurationService.distributionEnabled
        ])
    }
}
