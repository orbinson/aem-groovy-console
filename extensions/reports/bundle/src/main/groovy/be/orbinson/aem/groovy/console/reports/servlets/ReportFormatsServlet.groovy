package be.orbinson.aem.groovy.console.reports.servlets

import be.orbinson.aem.groovy.console.reports.ReportExporterRegistry
import groovy.util.logging.Slf4j
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.SlingHttpServletResponse
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import javax.servlet.Servlet
import javax.servlet.ServletException

/**
 * Available export formats, discovered from the registered exporters.
 *
 * <code>GET /bin/groovyconsole/reports/formats.json</code>
 */
@Component(service = Servlet, immediate = true, property = [
        "sling.servlet.paths=/bin/groovyconsole/reports/formats"
])
@Slf4j("LOG")
class ReportFormatsServlet extends AbstractReportsServlet {

    @Reference
    private ReportExporterRegistry exporterRegistry

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        writeJsonResponse(response, [
                formats: exporterRegistry.exporters.collect { exporter -> ReportJsonMapper.format(exporter) }
        ])
    }
}
