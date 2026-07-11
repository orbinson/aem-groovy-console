package be.orbinson.aem.groovy.console.reports.servlets

import be.orbinson.aem.groovy.console.configuration.ConfigurationService
import be.orbinson.aem.groovy.console.reports.ReportException
import be.orbinson.aem.groovy.console.reports.ReportOptionsService
import be.orbinson.aem.groovy.console.reports.ReportService
import be.orbinson.aem.groovy.console.reports.model.ReportParameterType
import groovy.util.logging.Slf4j
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.SlingHttpServletResponse
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import javax.servlet.Servlet
import javax.servlet.ServletException

import static javax.servlet.http.HttpServletResponse.*

/**
 * Resolves the options of a {@code DYNAMIC} parameter by running its Groovy options script.
 *
 * <code>POST /bin/groovyconsole/reports/options</code> with JSON body either:
 * <ul>
 *     <li><code>{ name, parameterName, parameters?: { ... } }</code> — runs the <em>saved</em> parameter's
 *         script (read access to the report is the right to run it, as for report execution); or</li>
 *     <li><code>{ script, parameters?: { ... } }</code> — runs an inline script for the editor "try out"
 *         (requires console permission, like a report preview, since it runs arbitrary Groovy).</li>
 * </ul>
 * <code>parameters</code> supplies the values of already-entered fields so options can depend on them.
 * Returns <code>{ options: [ { value, label } ] }</code>.
 */
@Component(service = Servlet, immediate = true, property = [
        "sling.servlet.paths=/bin/groovyconsole/reports/options"
])
@Slf4j("LOG")
class ReportOptionsServlet extends AbstractReportsServlet {

    @Reference
    private ReportService reportService

    @Reference
    private ReportOptionsService optionsService

    @Reference
    private ConfigurationService configurationService

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        def body = readJsonBody(request)

        if (!body) {
            writeError(response, SC_BAD_REQUEST, "A JSON body is required.")

            return
        }

        def resolver = request.resourceResolver
        def script = resolveScript(request, body, response)

        if (script == null) {
            // resolveScript already wrote the error/empty response
            return
        }

        def parameterValues = AbstractReportsServlet.toParameterValues(body["parameters"] as Map)

        try {
            def options = optionsService.resolveOptions(script, parameterValues, resolver)

            writeJsonResponse(response, [options: options])
        } catch (ReportException e) {
            LOG.warn("error resolving dynamic options", e)

            writeError(response, SC_INTERNAL_SERVER_ERROR, e.message)
        }
    }

    // Resolve the script to run and enforce the matching authorization. Returns null (after writing a response)
    // when the request is invalid or not permitted.
    private String resolveScript(SlingHttpServletRequest request, Map body, SlingHttpServletResponse response) {
        def resolver = request.resourceResolver

        if (body["name"] && body["parameterName"]) {
            def definition = reportService.getReport(resolver, body["name"] as String)

            if (!definition) {
                writeError(response, SC_NOT_FOUND, "Report not found: ${body["name"]}")

                return null
            }

            def parameter = definition.parameters.find { it.name == body["parameterName"] }

            if (!parameter || parameter.type != ReportParameterType.DYNAMIC) {
                writeError(response, SC_BAD_REQUEST, "No dynamic parameter named: ${body["parameterName"]}")

                return null
            }

            return parameter.optionsScript ?: ""
        }

        if (body["script"]) {
            // an inline script runs arbitrary Groovy as the user, so it needs console permission (like a preview)
            if (!configurationService.hasPermission(request)) {
                writeError(response, SC_FORBIDDEN, "Not allowed to run dynamic options scripts.")

                return null
            }

            return body["script"] as String
        }

        writeError(response, SC_BAD_REQUEST, "A 'script', or a 'name' and 'parameterName', is required.")

        null
    }
}
