package be.orbinson.aem.groovy.console.reports.servlets

import be.orbinson.aem.groovy.console.reports.ReportException
import be.orbinson.aem.groovy.console.reports.ReportExecutionService
import be.orbinson.aem.groovy.console.reports.ReportService
import groovy.util.logging.Slf4j
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.SlingHttpServletResponse
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import javax.servlet.Servlet
import javax.servlet.ServletException

import static javax.servlet.http.HttpServletResponse.*

/**
 * Starts a report execution and returns immediately with a RUNNING execution; the script runs asynchronously
 * and the result is persisted. Clients poll <code>execution.json</code> for the outcome.
 *
 * <code>POST /bin/groovyconsole/reports/execute</code> with JSON body
 * <code>{"name": "...", "parameters": {"key": "value"}}</code>.
 */
@Component(service = Servlet, immediate = true, property = [
        "sling.servlet.paths=/bin/groovyconsole/reports/execute"
])
@Slf4j("LOG")
class ReportExecuteServlet extends AbstractReportsServlet {

    @Reference
    private ReportService reportService

    @Reference
    private ReportExecutionService executionService

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        // running a report executes only dev/admin-authored, vetted scripts (authoring is gated separately),
        // so read access to the report definition alone is the right to run it — business users with read-only
        // permissions run reports with their own session
        def body = readJsonBody(request)

        if (!body || !body["name"]) {
            writeError(response, SC_BAD_REQUEST, "A JSON body with a 'name' property is required.")

            return
        }

        def name = body["name"] as String
        // read access (via the user's resolver) is the right to run; null = not found or not permitted
        def definition = reportService.getReport(request.resourceResolver, name)

        if (!definition) {
            writeError(response, SC_NOT_FOUND, "Report not found: $name")

            return
        }

        def parameterValues = AbstractReportsServlet.toParameterValues(body["parameters"] as Map)

        try {
            def execution = executionService.execute(definition, parameterValues, request.resourceResolver)

            writeJsonResponse(response, ReportJsonMapper.execution(execution))
        } catch (IllegalArgumentException e) {
            writeError(response, SC_BAD_REQUEST, e.message)
        } catch (ReportException e) {
            LOG.error("error executing report : {}", name, e)

            writeError(response, SC_INTERNAL_SERVER_ERROR, e.message)
        }
    }
}
