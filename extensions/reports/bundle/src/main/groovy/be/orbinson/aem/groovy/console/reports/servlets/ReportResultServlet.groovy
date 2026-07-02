package be.orbinson.aem.groovy.console.reports.servlets

import be.orbinson.aem.groovy.console.reports.ReportExecutionService
import be.orbinson.aem.groovy.console.reports.ReportResultStore
import be.orbinson.aem.groovy.console.reports.ReportService
import groovy.util.logging.Slf4j
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.SlingHttpServletResponse
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import javax.servlet.Servlet
import javax.servlet.ServletException

import static be.orbinson.aem.groovy.console.reports.constants.ReportsConstants.*
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND

/**
 * Paged access to a persisted execution result.
 *
 * <code>GET /bin/groovyconsole/reports/result.json?executionId=&amp;page=&amp;pageSize=</code>
 *
 * Pages are 1-based.  When no page size is given, the report's page size (or the configured default) applies.
 */
@Component(service = Servlet, immediate = true, property = [
        "sling.servlet.paths=/bin/groovyconsole/reports/result"
])
@Slf4j("LOG")
class ReportResultServlet extends AbstractReportsServlet {

    @Reference
    private ReportService reportService

    @Reference
    private ReportExecutionService executionService

    @Reference
    private ReportResultStore resultStore

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        def executionId = request.getParameter(PARAMETER_EXECUTION_ID)

        def execution = executionId ? executionService.getExecution(executionId) : null

        if (!execution) {
            writeError(response, SC_NOT_FOUND, "Execution not found: $executionId")

            return
        }

        // access follows the report's read ACL; orphaned executions need the report-create capability
        def resolver = request.resourceResolver
        def definition = execution.reportName ? reportService.getReport(resolver, execution.reportName) : null

        if (!definition && !reportService.canCreate(resolver)) {
            writeError(response, SC_FORBIDDEN, "Not allowed to view execution: $executionId")

            return
        }

        def page = toInt(request.getParameter(PARAMETER_PAGE), 1)
        def pageSize = toInt(request.getParameter(PARAMETER_PAGE_SIZE), definition?.pageSize ?: 0)

        def resultPage = resultStore.getPage(executionId, page, pageSize)

        if (!resultPage) {
            writeError(response, SC_NOT_FOUND, "No result available for execution: $executionId")

            return
        }

        writeJsonResponse(response, ReportJsonMapper.resultPage(resultPage))
    }

    // internals

    private static int toInt(String value, int defaultValue) {
        try {
            value ? Integer.parseInt(value) : defaultValue
        } catch (NumberFormatException ignored) {
            defaultValue
        }
    }
}
