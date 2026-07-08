package be.orbinson.aem.groovy.console.reports.servlets

import be.orbinson.aem.groovy.console.configuration.ConfigurationService
import be.orbinson.aem.groovy.console.reports.ReportExecutionService
import be.orbinson.aem.groovy.console.reports.ReportService
import groovy.util.logging.Slf4j
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.SlingHttpServletResponse
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import javax.servlet.Servlet
import javax.servlet.ServletException

import static be.orbinson.aem.groovy.console.reports.constants.ReportsConstants.PARAMETER_EXECUTION_ID
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND

/**
 * Single execution status/details endpoint.
 *
 * <code>GET /bin/groovyconsole/reports/execution.json?executionId=</code>
 */
@Component(service = Servlet, immediate = true, property = [
        "sling.servlet.paths=/bin/groovyconsole/reports/execution"
])
@Slf4j("LOG")
class ReportExecutionServlet extends AbstractReportsServlet {

    @Reference
    private ReportService reportService

    @Reference
    private ReportExecutionService executionService

    @Reference
    private ConfigurationService configurationService

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        def executionId = request.getParameter(PARAMETER_EXECUTION_ID)

        def execution = executionId ? executionService.getExecution(executionId) : null

        if (!execution) {
            writeError(response, SC_NOT_FOUND, "Execution not found: $executionId")

            return
        }

        if (!canViewExecution(request, execution, reportService, configurationService)) {
            writeError(response, SC_FORBIDDEN, "Not allowed to view execution: $executionId")

            return
        }

        writeJsonResponse(response, ReportJsonMapper.execution(execution))
    }
}
