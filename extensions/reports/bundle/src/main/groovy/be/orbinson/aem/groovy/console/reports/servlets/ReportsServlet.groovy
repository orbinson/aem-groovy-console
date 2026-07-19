package be.orbinson.aem.groovy.console.reports.servlets

import be.orbinson.aem.groovy.console.configuration.ConfigurationService
import be.orbinson.aem.groovy.console.reports.ReportException
import be.orbinson.aem.groovy.console.reports.ReportExporterRegistry
import be.orbinson.aem.groovy.console.reports.ReportScheduleService
import be.orbinson.aem.groovy.console.reports.ReportService
import be.orbinson.aem.groovy.console.reports.model.ReportDefinition
import be.orbinson.aem.groovy.console.reports.model.ReportDistributionTarget
import be.orbinson.aem.groovy.console.reports.model.ReportParameter
import be.orbinson.aem.groovy.console.reports.model.ReportParameterType
import be.orbinson.aem.groovy.console.reports.impl.ReportImpersonation
import be.orbinson.aem.groovy.console.reports.model.ReportSchedule
import groovy.util.logging.Slf4j
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.SlingHttpServletResponse
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import javax.servlet.Servlet
import javax.servlet.ServletException

import static be.orbinson.aem.groovy.console.reports.constants.ReportsConstants.EXECUTOR_SYSTEM_USER_NAME
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
    private ReportScheduleService scheduleService

    @Reference
    private ConfigurationService configurationService

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        def resolver = request.resourceResolver
        def name = request.getParameter(PARAMETER_NAME)

        // authoring a report writes server-side executable Groovy, so it requires console permission in
        // addition to JCR write access; reflect that in the capability flags the UI uses to show edit actions
        def consolePermitted = configurationService.hasPermission(request)

        if (name) {
            // null = not found OR not readable; JCR read access alone governs visibility
            def definition = reportService.getReport(resolver, name)

            if (!definition) {
                writeError(response, SC_NOT_FOUND, "Report not found: $name")
            } else {
                writeJsonResponse(response, ReportJsonMapper.definition(definition,
                        consolePermitted && reportService.canEdit(resolver, name), exporterRegistry.exporters))
            }
        } else {
            def reports = reportService.getReports(resolver).collect { definition ->
                ReportJsonMapper.summary(definition,
                        consolePermitted && reportService.canEdit(resolver, definition.name))
            }

            writeJsonResponse(response,
                    [reports: reports, canManage: consolePermitted && reportService.canCreate(resolver)])
        }
    }

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        // saving a report writes server-side executable Groovy, so it requires console permission on top of
        // the JCR write check inside saveReport
        if (!configurationService.hasPermission(request)) {
            writeError(response, SC_FORBIDDEN, "Not allowed to save reports.")

            return
        }

        def body = readJsonBody(request)

        if (!body || !body["name"]) {
            writeError(response, SC_BAD_REQUEST, "A JSON body with a 'name' property is required.")

            return
        }

        def resolver = request.resourceResolver
        def name = body["name"] as String

        // authorization is the JCR write check inside saveReport (PersistenceException -> 403)
        try {
            def definition = reportService.saveReport(resolver, fromBody(body), resolver.userID)

            // register/refresh/remove the cron job to match the saved schedule
            scheduleService.reconcile(definition)

            // enable the executor to run this scheduled report as its author (self-scoped, best-effort)
            if (definition.schedule?.enabled) {
                ReportImpersonation.grantSelfImpersonation(resolver, EXECUTOR_SYSTEM_USER_NAME)
            }

            writeJsonResponse(response, ReportJsonMapper.definition(definition,
                    reportService.canEdit(resolver, name), exporterRegistry.exporters))
        } catch (IllegalArgumentException e) {
            writeError(response, SC_BAD_REQUEST, e.message)
        } catch (ReportException e) {
            LOG.warn("error saving report : {}", name, e)

            // covers both the JCR write check and the run-as impersonation gate; surface the reason
            writeError(response, SC_FORBIDDEN, e.message ?: "Not allowed to save report: $name")
        }
    }

    @Override
    protected void doDelete(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        // deleting a report does not introduce or change executable Groovy, so it is not gated by the console
        // permission — JCR delete access on the report node governs it
        def resolver = request.resourceResolver
        def name = request.getParameter(PARAMETER_NAME)
        def definition = reportService.getReport(resolver, name)

        if (!definition) {
            writeError(response, SC_NOT_FOUND, "Report not found: $name")

            return
        }

        try {
            reportService.deleteReport(resolver, name)

            // remove any cron job for the deleted report
            scheduleService.unschedule(definition.path)

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
                },
                schedule: body["schedule"] ? scheduleFromBody(body["schedule"] as Map) : null,
                distributions: (body["distributions"] as List ?: []).collect { target ->
                    distributionFromBody(target as Map)
                }
        )
    }

    // runAs and scheduledBy are deliberately NOT read from the request body: the service forces both to the
    // requesting user, so a schedule saved through the UI/API always runs as its author and cannot be pointed
    // at another user by a forged body
    static ReportSchedule scheduleFromBody(Map schedule) {
        new ReportSchedule(
                enabled: Boolean.TRUE == schedule["enabled"],
                cronExpression: schedule["cronExpression"] as String,
                parameterValues: (schedule["parameterValues"] as Map ?: [:]).collectEntries { key, value ->
                    [(key as String): value == null ? null : value as String]
                } as Map<String, String>
        )
    }

    static ReportDistributionTarget distributionFromBody(Map target) {
        new ReportDistributionTarget(
                distributorId: target["distributorId"] as String,
                format: target["format"] as String,
                config: (target["config"] as Map ?: [:]) as Map<String, Object>
        )
    }

    static ReportParameter parameterFromBody(Map parameter, int index) {
        new ReportParameter(
                name: parameter["name"] as String,
                label: parameter["label"] as String,
                type: toParameterType(parameter["type"] as String),
                defaultValue: parameter["defaultValue"] == null ? null : parameter["defaultValue"] as String,
                required: Boolean.TRUE == parameter["required"],
                options: (parameter["options"] as List ?: []).collect { it as String }.findAll(),
                pathType: parameter["pathType"] ? parameter["pathType"] as String : "NODE",
                rootPath: parameter["rootPath"] == null ? null : parameter["rootPath"] as String,
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
