package be.orbinson.aem.groovy.console.reports.servlets

import be.orbinson.aem.groovy.console.configuration.ConfigurationService
import be.orbinson.aem.groovy.console.reports.ReportException
import be.orbinson.aem.groovy.console.reports.ReportExporterRegistry
import be.orbinson.aem.groovy.console.reports.ReportService
import be.orbinson.aem.groovy.console.reports.model.ReportDefinition
import be.orbinson.aem.groovy.console.reports.model.ReportParameter
import be.orbinson.aem.groovy.console.reports.model.ReportParameterType
import groovy.transform.PackageScope
import groovy.util.logging.Slf4j
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.SlingHttpServletResponse
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import javax.servlet.Servlet
import javax.servlet.ServletException

import static be.orbinson.aem.groovy.console.reports.constants.ReportsConstants.PARAMETER_NAME
import static javax.servlet.http.HttpServletResponse.*

/**
 * CRUD endpoint for report definitions.
 *
 * <ul>
 *     <li><code>GET /bin/groovyconsole/reports.json</code> - list reports visible to the user</li>
 *     <li><code>GET /bin/groovyconsole/reports.json?name=</code> - single definition</li>
 *     <li><code>POST /bin/groovyconsole/reports</code> - create or update a definition (JSON body)</li>
 *     <li><code>DELETE /bin/groovyconsole/reports?name=</code> - delete a definition</li>
 * </ul>
 */
@Component(service = Servlet, immediate = true, property = [
        "sling.servlet.paths=/bin/groovyconsole/reports"
])
@Slf4j("LOG")
class ReportsServlet extends AbstractReportsServlet {

    @Reference
    private ReportService reportService

    @Reference
    private ReportExporterRegistry exporterRegistry

    @Reference
    private ConfigurationService configurationService

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        def resolver = request.resourceResolver
        def name = request.getParameter(PARAMETER_NAME)

        // metadata/parameter edits need only JCR write access (canEdit); editing the executable Groovy (report
        // script + dynamic options scripts) additionally requires console permission, and so does creating a
        // report (which establishes a script). The UI uses these flags to gate edit actions and the script editor.
        def consolePermitted = configurationService.hasPermission(request)

        if (name) {
            // null = not found OR not readable; JCR read access alone governs visibility
            def definition = reportService.getReport(resolver, name)

            if (!definition) {
                writeError(response, SC_NOT_FOUND, "Report not found: $name")
            } else {
                def canEdit = reportService.canEdit(resolver, name)

                writeJsonResponse(response, ReportJsonMapper.definition(definition, canEdit,
                        consolePermitted && canEdit, exporterRegistry.exporters))
            }
        } else {
            def reports = reportService.getReports(resolver).collect { definition ->
                ReportJsonMapper.summary(definition, reportService.canEdit(resolver, definition.name))
            }

            writeJsonResponse(response,
                    [reports: reports, canManage: consolePermitted && reportService.canCreate(resolver)])
        }
    }

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        def body = readJsonBody(request)

        if (!body || !body["name"]) {
            writeError(response, SC_BAD_REQUEST, "A JSON body with a 'name' property is required.")

            return
        }

        def resolver = request.resourceResolver
        def name = body["name"] as String
        def submitted = fromBody(body)

        // The report script and any dynamic-options scripts are executable Groovy, so writing them requires
        // console permission; metadata/parameter edits need only JCR write access. Callers without console
        // permission may edit an existing report's metadata but cannot introduce or change its scripts, and
        // cannot create a report (which would establish a script).
        def consolePermitted = configurationService.hasPermission(request)
        def existing = reportService.getReport(resolver, name)

        if (!consolePermitted) {
            if (!existing) {
                writeError(response, SC_FORBIDDEN,
                        "Creating a report requires console permission (reports run Groovy scripts).")

                return
            }

            preserveScripts(submitted, existing)
        }

        // JCR write access is enforced inside saveReport (PersistenceException -> ReportException -> 403)
        try {
            def definition = reportService.saveReport(resolver, submitted, resolver.userID)
            def canEdit = reportService.canEdit(resolver, name)

            writeJsonResponse(response, ReportJsonMapper.definition(definition, canEdit,
                    consolePermitted && canEdit, exporterRegistry.exporters))
        } catch (IllegalArgumentException e) {
            writeError(response, SC_BAD_REQUEST, e.message)
        } catch (ReportException e) {
            LOG.warn("error saving report : {}", name, e)

            writeError(response, SC_FORBIDDEN, "Not allowed to save report (check repository permissions): $name")
        }
    }

    /**
     * Carry the existing (vetted) executable Groovy forward onto a submitted definition, ignoring any script
     * changes in the request.  Used when the caller lacks console permission, so they can edit metadata and
     * parameter configuration but never introduce or alter a runnable script.
     */
    @PackageScope
    static void preserveScripts(ReportDefinition submitted, ReportDefinition existing) {
        submitted.script = existing.script

        def existingByName = existing.parameters.collectEntries { [(it.name): it] }

        submitted.parameters.each { parameter ->
            if (parameter.type == ReportParameterType.DYNAMIC) {
                parameter.optionsScript = (existingByName[parameter.name] as ReportParameter)?.optionsScript
            }
        }
    }

    @Override
    protected void doDelete(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        // deleting a report does not introduce or change executable Groovy, so it is not gated by the console
        // permission — JCR delete access on the report node governs it
        def resolver = request.resourceResolver
        def name = request.getParameter(PARAMETER_NAME)

        if (!reportService.getReport(resolver, name)) {
            writeError(response, SC_NOT_FOUND, "Report not found: $name")

            return
        }

        try {
            reportService.deleteReport(resolver, name)

            writeJsonResponse(response, [deleted: name])
        } catch (ReportException e) {
            LOG.warn("error deleting report : {}", name, e)

            writeError(response, SC_FORBIDDEN, "Not allowed to delete report (check repository permissions): $name")
        }
    }

    // internals

    static ReportDefinition fromBody(Map body) {
        new ReportDefinition(
                name: body["name"] as String,
                title: body["title"] as String,
                description: body["description"] as String,
                category: body["category"] as String,
                script: body["script"] as String,
                pageSize: body["pageSize"] != null ? (body["pageSize"] as Integer) : null,
                parameters: (body["parameters"] as List ?: []).withIndex().collect { parameter, index ->
                    parameterFromBody(parameter as Map, index as int)
                }
        )
    }

    static ReportParameter parameterFromBody(Map parameter, int index) {
        new ReportParameter(
                name: parameter["name"] as String,
                label: parameter["label"] as String,
                type: toParameterType(parameter["type"] as String),
                defaultValue: parameter["defaultValue"] == null ? null : parameter["defaultValue"] as String,
                required: Boolean.TRUE == parameter["required"],
                multiple: Boolean.TRUE == parameter["multiple"],
                options: (parameter["options"] as List ?: []).collect { it as String }.findAll(),
                pathType: parameter["pathType"] ? parameter["pathType"] as String : "NODE",
                rootPath: parameter["rootPath"] == null ? null : parameter["rootPath"] as String,
                optionsScript: parameter["optionsScript"] == null ? null : parameter["optionsScript"] as String,
                order: parameter["order"] != null ? (parameter["order"] as Integer) : index
        )
    }

    static ReportParameterType toParameterType(String type) {
        try {
            type ? ReportParameterType.valueOf(type.toUpperCase()) : ReportParameterType.STRING
        } catch (IllegalArgumentException ignored) {
            ReportParameterType.STRING
        }
    }
}
