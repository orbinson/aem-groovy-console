package be.orbinson.aem.groovy.console.reports.servlets

import be.orbinson.aem.groovy.console.reports.LocaleAwareReportExporter
import be.orbinson.aem.groovy.console.reports.ReportExecutionService
import be.orbinson.aem.groovy.console.reports.ReportExporterRegistry
import be.orbinson.aem.groovy.console.reports.ReportResultStore
import be.orbinson.aem.groovy.console.reports.ReportService
import be.orbinson.aem.groovy.console.reports.data.ReportColumnType
import be.orbinson.aem.groovy.console.reports.data.ReportData
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

        // exports are opened outside the browser, so relative LINK hrefs would not resolve; rewrite them to
        // absolute URLs using the request. Falls back to leaving hrefs untouched if no base URL is available.
        def baseUrl = requestBaseUrl(request)

        if (baseUrl) {
            absolutizeLinks(reportData, baseUrl)
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

    // internals

    // scheme://host[:port]<contextPath> from the request; null when no server name is available (fallback:
    // callers leave links relative)
    private static String requestBaseUrl(SlingHttpServletRequest request) {
        def serverName = request?.serverName

        if (!serverName) {
            return null
        }

        def scheme = request.scheme ?: "http"
        def port = request.serverPort
        def url = new StringBuilder(scheme).append("://").append(serverName)

        if (port > 0 && !(scheme == "http" && port == 80) && !(scheme == "https" && port == 443)) {
            url.append(":").append(port)
        }

        url.append(request.contextPath ?: "")

        url.toString()
    }

    private static void absolutizeLinks(ReportData reportData, String baseUrl) {
        def linkColumns = (0..<reportData.columns.size()).findAll { index ->
            reportData.columns[index].type == ReportColumnType.LINK
        }

        if (!linkColumns) {
            return
        }

        reportData.rows.each { row ->
            linkColumns.each { index ->
                def cell = index < row.size() ? row[index] : null

                if (cell instanceof Map && cell["href"]) {
                    cell["href"] = toAbsoluteUrl(cell["href"] as String, baseUrl)
                }
            }
        }
    }

    // leaves already-absolute (scheme:... or //host) hrefs untouched; prefixes relative ones with the base URL
    private static String toAbsoluteUrl(String href, String baseUrl) {
        if (href ==~ /(?i)[a-z][a-z0-9+.\-]*:.*/ || href.startsWith("//")) {
            return href
        }

        baseUrl + (href.startsWith("/") ? href : "/$href")
    }
}
