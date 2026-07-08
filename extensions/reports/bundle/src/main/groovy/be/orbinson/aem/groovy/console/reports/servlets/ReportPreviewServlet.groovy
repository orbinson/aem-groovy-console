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

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR

/**
 * Ephemeral "try out" run for the report editor: runs the posted (possibly unsaved) report definition with
 * test values through the real report pipeline and returns the typed result inline — nothing is persisted.
 *
 * <code>POST /bin/groovyconsole/reports/preview</code> with JSON body
 * <code>{ name?, script | scriptPath, parameters: [...], values: { ... } }</code>.
 */
@Component(service = Servlet, immediate = true, property = [
        "sling.servlet.paths=/bin/groovyconsole/reports/preview"
])
@Slf4j("LOG")
class ReportPreviewServlet extends AbstractReportsServlet {

    @Reference
    private ReportService reportService

    @Reference
    private ReportExecutionService executionService

    @Reference
    private ConfigurationService configurationService

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        // preview runs arbitrary Groovy as the user, so it requires console permission (as running a console
        // script does) on top of the JCR author check below
        if (!configurationService.hasPermission(request)) {
            writeError(response, SC_FORBIDDEN, "Not allowed to run report previews.")

            return
        }

        def body = readJsonBody(request)

        if (!body || !body["script"]) {
            writeError(response, SC_BAD_REQUEST, "A JSON body with a 'script' is required.")

            return
        }

        def resolver = request.resourceResolver
        def definition = ReportsServlet.fromBody(body)

        // author check: editors of the existing report, or users who may create reports for a new/unsaved one
        def allowed = definition.name && reportService.getReport(resolver, definition.name) ?
                reportService.canEdit(resolver, definition.name) : reportService.canCreate(resolver)

        if (!allowed) {
            writeError(response, SC_FORBIDDEN, "Not allowed to run this report preview.")

            return
        }

        def values = (body["values"] as Map ?: [:]).collectEntries { key, value ->
            [(key as String): value == null ? null : value as String]
        } as Map<String, String>

        try {
            def preview = executionService.preview(definition, values, request.resourceResolver)

            writeJsonResponse(response, ReportJsonMapper.preview(preview))
        } catch (IllegalArgumentException e) {
            writeError(response, SC_BAD_REQUEST, e.message)
        } catch (Exception e) {
            LOG.error("error previewing report", e)

            writeError(response, SC_INTERNAL_SERVER_ERROR, e.message)
        }
    }
}
