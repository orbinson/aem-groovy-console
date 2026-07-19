package be.orbinson.aem.groovy.console.reports.servlets

import be.orbinson.aem.groovy.console.reports.ReportDistributorRegistry
import be.orbinson.aem.groovy.console.reports.ReportExporterRegistry
import groovy.util.logging.Slf4j
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.SlingHttpServletResponse
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import javax.servlet.Servlet
import javax.servlet.ServletException

/**
 * Available distributors and export formats, discovered from the registered services.  Used by the report editor
 * to offer distribution targets.
 *
 * <code>GET /bin/groovyconsole/reports/distributors.json</code>
 */
@Component(service = Servlet, immediate = true, property = [
        "sling.servlet.paths=/bin/groovyconsole/reports/distributors"
])
@Slf4j("LOG")
class ReportDistributorsServlet extends AbstractReportsServlet {

    @Reference
    private ReportDistributorRegistry distributorRegistry

    @Reference
    private ReportExporterRegistry exporterRegistry

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        writeJsonResponse(response, [
                distributors: distributorRegistry.distributors.collect { distributor ->
                    ReportJsonMapper.distributor(distributor)
                },
                formats     : exporterRegistry.exporters.collect { exporter -> ReportJsonMapper.format(exporter) }
        ])
    }
}
