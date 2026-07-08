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
import static be.orbinson.aem.groovy.console.reports.constants.ReportsConstants.PARAMETER_NAME
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND

/**
 * Execution history endpoint.
 *
 * <ul>
 *     <li><code>GET /bin/groovyconsole/reports/executions.json?name=</code> - executions of a report</li>
 *     <li><code>DELETE /bin/groovyconsole/reports/executions?executionId=</code> - delete one execution</li>
 * </ul>
 */
@Component(service = Servlet, immediate = true, property = [
        "sling.servlet.paths=/bin/groovyconsole/reports/executions"
])
@Slf4j("LOG")
class ReportExecutionsServlet extends AbstractReportsServlet {

    @Reference
    private ReportService reportService

    @Reference
    private ReportExecutionService executionService

    @Reference
    private ConfigurationService configurationService

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        def name = request.getParameter(PARAMETER_NAME)

        // read access to the report definition governs visibility of its executions
        def definition = name ? reportService.getReport(request.resourceResolver, name) : null

        if (!definition) {
            writeError(response, SC_NOT_FOUND, "Report not found: $name")

            return
        }

        def executions = executionService.getExecutions(name).collect { execution ->
            ReportJsonMapper.execution(execution)
        }

        writeJsonResponse(response, [executions: executions])
    }

    @Override
    protected void doDelete(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        def executionId = request.getParameter(PARAMETER_EXECUTION_ID)

        def execution = executionId ? executionService.getExecution(executionId) : null

        if (!execution) {
            writeError(response, SC_NOT_FOUND, "Execution not found: $executionId")

            return
        }

        if (!canManageExecution(request, execution, reportService, configurationService)) {
            writeError(response, SC_FORBIDDEN, "Not allowed to delete execution: $executionId")

            return
        }

        executionService.deleteExecution(executionId)

        writeJsonResponse(response, [deleted: executionId])
    }
}
