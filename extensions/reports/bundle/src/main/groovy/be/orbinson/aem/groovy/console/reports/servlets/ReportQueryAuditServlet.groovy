package be.orbinson.aem.groovy.console.reports.servlets

import be.orbinson.aem.groovy.console.configuration.ConfigurationService
import be.orbinson.aem.groovy.console.reports.ReportQueryAuditService
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
 * Query audit for the report editor: runs the posted (possibly unsaved) report definition with test values and
 * reports, per JCR query it executes, whether the live Oak repository has a covering index. Lets report editors
 * verify a report — an often-run query — is index-backed. Like the preview it persists nothing.
 *
 * <ul>
 *   <li><code>GET /bin/groovyconsole/reports/query-audit.json</code> &rarr; <code>{ available }</code>: whether the
 *       optional query-audit extension is installed (the UI uses this to decide whether to offer the action).</li>
 *   <li><code>POST /bin/groovyconsole/reports/query-audit</code> with JSON body
 *       <code>{ name?, script, parameters: [...], values: { ... } }</code> &rarr; the audit result.</li>
 * </ul>
 */
@Component(service = Servlet, immediate = true, property = [
        "sling.servlet.paths=/bin/groovyconsole/reports/query-audit"
])
@Slf4j("LOG")
class ReportQueryAuditServlet extends AbstractReportsServlet {

    @Reference
    private ReportService reportService

    @Reference
    private ReportQueryAuditService queryAuditService

    @Reference
    private ConfigurationService configurationService

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        if (!configurationService.hasPermission(request)) {
            writeError(response, SC_FORBIDDEN, "Not allowed to audit report queries.")

            return
        }

        writeJsonResponse(response, [available: queryAuditService.available])
    }

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        // like preview, this runs arbitrary Groovy as the user, so it requires console permission on top of the
        // JCR author check below
        if (!configurationService.hasPermission(request)) {
            writeError(response, SC_FORBIDDEN, "Not allowed to audit report queries.")

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
            writeError(response, SC_FORBIDDEN, "Not allowed to audit this report.")

            return
        }

        def values = (body["values"] as Map ?: [:]).collectEntries { key, value ->
            [(key as String): value == null ? null : value as String]
        } as Map<String, String>

        try {
            def audit = queryAuditService.audit(definition, values, resolver)

            writeJsonResponse(response, ReportJsonMapper.queryAudit(audit))
        } catch (IllegalArgumentException e) {
            writeError(response, SC_BAD_REQUEST, e.message)
        } catch (Exception e) {
            LOG.error("error auditing report queries", e)

            writeError(response, SC_INTERNAL_SERVER_ERROR, e.message)
        }
    }
}
