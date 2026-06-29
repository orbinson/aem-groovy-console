package be.orbinson.aem.groovy.console.reports.servlets

import be.orbinson.aem.groovy.console.reports.LocaleAwareReportExporter
import be.orbinson.aem.groovy.console.reports.ReportExecutionService
import be.orbinson.aem.groovy.console.reports.ReportExporterRegistry
import be.orbinson.aem.groovy.console.reports.ReportResultStore
import be.orbinson.aem.groovy.console.reports.ReportService
import groovy.util.logging.Slf4j
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.SlingHttpServletResponse
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import javax.servlet.Servlet
import javax.servlet.ServletException

import static be.orbinson.aem.groovy.console.reports.constants.ReportsConstants.CHARSET
import static be.orbinson.aem.groovy.console.reports.constants.ReportsConstants.PARAMETER_EXECUTION_ID
import static be.orbinson.aem.groovy.console.reports.constants.ReportsConstants.PARAMETER_FORMAT
import static javax.servlet.http.HttpServletResponse.*

/**
 * Streamed export of a persisted execution result.
 *
 * <code>GET /bin/groovyconsole/reports/export?executionId=&amp;format=</code>
 *
 * The format must match a registered exporter (see the formats endpoint).
 */
@Component(service = Servlet, immediate = true, property = [
        "sling.servlet.paths=/bin/groovyconsole/reports/export"
])
@Slf4j("LOG")
class ReportExportServlet extends AbstractReportsServlet {

    private static final String DATE_FORMAT_FILE_NAME = "yyyy-MM-dd'T'HHmmss"

    @Reference
    private ReportService reportService

    @Reference
    private ReportExecutionService executionService

    @Reference
    private ReportResultStore resultStore

    @Reference
    private ReportExporterRegistry exporterRegistry

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        def executionId = request.getParameter(PARAMETER_EXECUTION_ID)
        def format = request.getParameter(PARAMETER_FORMAT)

        def exporter = format ? exporterRegistry.getExporter(format) : null

        if (!exporter) {
            writeError(response, SC_BAD_REQUEST, "Unknown export format: $format")

            return
        }

        def execution = executionId ? executionService.getExecution(executionId) : null

        if (!execution) {
            writeError(response, SC_NOT_FOUND, "Execution not found: $executionId")

            return
        }

        // access follows the report's read ACL; orphaned executions need the report-create capability
        def resolver = request.resourceResolver
        def allowed = execution.reportName && reportService.getReport(resolver, execution.reportName) ?
                true : reportService.canCreate(resolver)

        if (!allowed) {
            writeError(response, SC_FORBIDDEN, "Not allowed to export execution: $executionId")

            return
        }

        def reportData = resultStore.getData(executionId)

        if (!reportData) {
            writeError(response, SC_NOT_FOUND, "No result available for execution: $executionId")

            return
        }

        def fileName = new StringBuilder()
                .append(execution.reportName ?: "report")
                .append("-")
                .append((execution.startedAt ?: Calendar.instance).format(DATE_FORMAT_FILE_NAME))
                .append(".")
                .append(exporter.fileExtension)
                .toString()

        response.contentType = exporter.contentType
        response.characterEncoding = CHARSET
        response.setHeader("Content-Disposition", "attachment; filename=\"$fileName\"")

        try {
            if (exporter instanceof LocaleAwareReportExporter) {
                exporter.export(reportData, response.outputStream, request.locale)
            } else {
                exporter.export(reportData, response.outputStream)
            }
            response.outputStream.flush()
        } catch (Exception e) {
            LOG.error("error exporting execution {} as format {}", executionId, format, e)

            // only an uncommitted response can still carry a JSON error; otherwise the download already streamed
            if (!response.committed) {
                response.reset()
                writeError(response, SC_INTERNAL_SERVER_ERROR, "Export failed for execution: $executionId")
            }
        }
    }
}
