package be.orbinson.aem.groovy.console.reports.servlets

import be.orbinson.aem.groovy.console.configuration.ConfigurationService
import be.orbinson.aem.groovy.console.reports.ReportException
import be.orbinson.aem.groovy.console.reports.ReportExecutionService
import be.orbinson.aem.groovy.console.reports.ReportService
import be.orbinson.aem.groovy.console.reports.impl.ReportsConfigurationService
import groovy.util.logging.Slf4j
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.SlingHttpServletResponse
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import javax.servlet.Servlet
import javax.servlet.ServletException

import static be.orbinson.aem.groovy.console.reports.constants.ReportsConstants.PARAMETER_EXECUTION_ID
import static javax.servlet.http.HttpServletResponse.*

/**
 * Distribute the result of an already-completed execution on demand.  Distribution can send report data to
 * external destinations, so it requires manage access to the execution's report.
 *
 * <code>POST /bin/groovyconsole/reports/distribute</code> with JSON body
 * <code>{"executionId": "...", "targets": [{"distributorId": "email", "format": "csv", "config": {...}}]}</code>.
 */
@Component(service = Servlet, immediate = true, property = [
        "sling.servlet.paths=/bin/groovyconsole/reports/distribute"
])
@Slf4j("LOG")
class ReportDistributeServlet extends AbstractReportsServlet {

    @Reference
    private ReportService reportService

    @Reference
    private ReportExecutionService executionService

    @Reference
    private ConfigurationService configurationService

    @Reference
    private ReportsConfigurationService reportsConfigurationService

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        if (!reportsConfigurationService.distributionEnabled) {
            writeError(response, SC_SERVICE_UNAVAILABLE, "Report distribution is disabled.")

            return
        }

        def body = readJsonBody(request)
        def executionId = body?.get(PARAMETER_EXECUTION_ID) as String

        if (!executionId) {
            writeError(response, SC_BAD_REQUEST, "A JSON body with an 'executionId' property is required.")

            return
        }

        def execution = executionService.getExecution(executionId)

        if (!execution) {
            writeError(response, SC_NOT_FOUND, "Execution not found: $executionId")

            return
        }

        if (!canManageExecution(request, execution, reportService, configurationService)) {
            writeError(response, SC_FORBIDDEN, "Not allowed to distribute this execution.")

            return
        }

        def targets = (body["targets"] as List ?: []).collect { target ->
            ReportsServlet.distributionFromBody(target as Map)
        }

        if (!targets) {
            writeError(response, SC_BAD_REQUEST, "At least one distribution target is required.")

            return
        }

        try {
            executionService.distribute(executionId, targets)

            writeJsonResponse(response, ReportJsonMapper.execution(executionService.getExecution(executionId)))
        } catch (ReportException e) {
            LOG.warn("error distributing execution : {}", executionId, e)

            writeError(response, SC_INTERNAL_SERVER_ERROR, e.message)
        }
    }
}
