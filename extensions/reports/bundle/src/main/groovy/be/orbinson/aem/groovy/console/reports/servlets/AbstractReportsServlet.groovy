package be.orbinson.aem.groovy.console.reports.servlets

import be.orbinson.aem.groovy.console.configuration.ConfigurationService
import be.orbinson.aem.groovy.console.reports.ReportService
import be.orbinson.aem.groovy.console.reports.model.ReportExecution
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.SlingHttpServletResponse
import org.apache.sling.api.servlets.SlingAllMethodsServlet

import static be.orbinson.aem.groovy.console.reports.constants.ReportsConstants.CHARSET

@Slf4j("LOG")
abstract class AbstractReportsServlet extends SlingAllMethodsServlet {

    void writeJsonResponse(SlingHttpServletResponse response, json) {
        response.contentType = "application/json"
        response.characterEncoding = CHARSET

        new JsonBuilder(json).writeTo(response.writer)
    }

    void writeError(SlingHttpServletResponse response, int status, String message) {
        response.status = status

        writeJsonResponse(response, [error: message, status: status])
    }

    /**
     * Whether the caller may read a persisted execution. Access follows the report's read ACL; for an orphaned
     * execution (its report was deleted or is no longer readable) only the user who ran it or a console-authorized
     * user qualifies — so report-create rights alone no longer expose other users' orphaned results.
     */
    static boolean canViewExecution(SlingHttpServletRequest request, ReportExecution execution,
                                    ReportService reportService, ConfigurationService configurationService) {
        def resolver = request.resourceResolver

        if (execution.reportName && reportService.getReport(resolver, execution.reportName)) {
            return true
        }

        execution.userId == resolver.userID || configurationService.hasPermission(request)
    }

    /**
     * Whether the caller may delete a persisted execution: edit access to its report, or — for an orphaned
     * execution — the user who ran it or a console-authorized user.
     */
    static boolean canManageExecution(SlingHttpServletRequest request, ReportExecution execution,
                                      ReportService reportService, ConfigurationService configurationService) {
        def resolver = request.resourceResolver

        if (execution.reportName && reportService.getReport(resolver, execution.reportName)) {
            return reportService.canEdit(resolver, execution.reportName)
        }

        execution.userId == resolver.userID || configurationService.hasPermission(request)
    }

    Map readJsonBody(SlingHttpServletRequest request) {
        try {
            def body = new JsonSlurper().parse(request.reader)

            body instanceof Map ? body : null
        } catch (Exception e) {
            LOG.debug("error parsing JSON request body", e)

            null
        }
    }
}
