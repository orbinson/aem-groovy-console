package be.orbinson.aem.groovy.console.migration.servlets

import be.orbinson.aem.groovy.console.migration.MigrationRun
import be.orbinson.aem.groovy.console.migration.MigrationRunOptions
import be.orbinson.aem.groovy.console.migration.MigrationScriptResult
import be.orbinson.aem.groovy.console.migration.MigrationService
import be.orbinson.aem.groovy.console.migration.MigrationStatus
import groovy.json.JsonBuilder
import groovy.util.logging.Slf4j
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.SlingHttpServletResponse
import org.apache.sling.api.servlets.SlingAllMethodsServlet
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import javax.servlet.Servlet
import javax.servlet.ServletException

import static be.orbinson.aem.groovy.console.constants.GroovyConsoleConstants.CHARSET
import static be.orbinson.aem.groovy.console.constants.GroovyConsoleConstants.DATE_FORMAT_DISPLAY
import static be.orbinson.aem.groovy.console.migration.MigrationConstants.*
import static javax.servlet.http.HttpServletResponse.*

@Component(service = Servlet, immediate = true, property = [
        "sling.servlet.paths=/bin/groovyconsole/migration"
])
@Slf4j("LOG")
class MigrationServlet extends SlingAllMethodsServlet {

    @Reference
    private MigrationService migrationService

    private static void writeJsonResponse(SlingHttpServletResponse response, json) {
        response.contentType = "application/json"
        response.characterEncoding = CHARSET

        new JsonBuilder(json).writeTo(response.writer)
    }

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        if (migrationService.hasPermission(request)) {
            def runId = request.getParameter(RUN_ID)

            if (runId) {
                def run = migrationService.getRun(runId)

                if (run) {
                    writeJsonResponse(response, runToMap(run, true))
                } else {
                    response.status = SC_NOT_FOUND
                }
            } else if (Boolean.parseBoolean(request.getParameter(REGISTRY))) {
                writeJsonResponse(response, [data: migrationService.registry.collect { state ->
                    [
                            scriptPath : state.scriptPath,
                            checksum   : state.checksum,
                            status     : state.status?.name() ?: "",
                            lastRunDate: state.lastRunDate?.format(DATE_FORMAT_DISPLAY) ?: "",
                            runningTime: state.runningTime,
                            always     : state.always,
                            pending    : state.pending
                    ]
                }])
            } else if (Boolean.parseBoolean(request.getParameter(PENDING))) {
                writeJsonResponse(response, [data: migrationService.pendingScripts])
            } else {
                writeJsonResponse(response, [
                        running: migrationService.running,
                        data   : migrationService.runs.collect { run -> runToMap(run, false) }
                ])
            }
        } else {
            response.status = SC_FORBIDDEN
        }
    }

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        if (migrationService.hasPermission(request)) {
            def options = new MigrationRunOptions(
                    trigger: TRIGGER_API,
                    dryRun: Boolean.parseBoolean(request.getParameter(DRY_RUN)),
                    path: request.getParameter(PATH),
                    data: request.getParameter(DATA)
            )

            try {
                if (Boolean.parseBoolean(request.getParameter(ASYNC))) {
                    def runId = migrationService.enqueue(options)

                    response.status = SC_ACCEPTED

                    writeJsonResponse(response, [
                            (RUN_ID): runId,
                            status  : MigrationStatus.RUNNING.name()
                    ])
                } else {
                    writeJsonResponse(response, runToMap(migrationService.run(options), true))
                }
            } catch (IllegalStateException e) {
                LOG.warn("unable to trigger migration run : {}", e.message)

                response.status = SC_CONFLICT

                writeJsonResponse(response, [error: e.message])
            } catch (Exception e) {
                LOG.error("error triggering migration run", e)

                response.status = SC_INTERNAL_SERVER_ERROR

                writeJsonResponse(response, [error: e.message ?: e.class.name])
            }
        } else {
            response.status = SC_FORBIDDEN
        }
    }

    private static Map runToMap(MigrationRun run, boolean includeResults) {
        def map = [
                (RUN_ID)   : run.runId,
                status     : run.status.name(),
                trigger    : run.trigger,
                startDate  : run.startDate?.format(DATE_FORMAT_DISPLAY) ?: "",
                endDate    : run.endDate?.format(DATE_FORMAT_DISPLAY) ?: "",
                runningTime: run.runningTime,
                error      : run.error ?: "",
                (PATH)     : run.path ?: "",
                executed   : run.results.count { result -> result.status == MigrationStatus.SUCCESS },
                failed     : run.results.count { result -> result.status == MigrationStatus.FAILED },
                skipped    : run.results.count { result -> result.status == MigrationStatus.SKIPPED },
                pending    : run.results.count { result -> result.status == MigrationStatus.PENDING }
        ]

        if (includeResults) {
            map.results = run.results.collect { result -> resultToMap(result) }
        }

        map
    }

    private static Map resultToMap(MigrationScriptResult result) {
        [
                scriptPath    : result.scriptPath,
                checksum      : result.checksum,
                status        : result.status.name(),
                runningTime   : result.runningTime,
                durationMillis: result.durationMillis,
                output        : result.output,
                error         : result.error
        ]
    }
}
