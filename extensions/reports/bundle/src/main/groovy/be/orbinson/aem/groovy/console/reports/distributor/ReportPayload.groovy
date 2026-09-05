package be.orbinson.aem.groovy.console.reports.distributor

import be.orbinson.aem.groovy.console.reports.ReportExporter
import be.orbinson.aem.groovy.console.reports.ReportExporterRegistry
import be.orbinson.aem.groovy.console.reports.ReportException
import be.orbinson.aem.groovy.console.reports.data.ReportData
import be.orbinson.aem.groovy.console.reports.model.ReportExecution
import groovy.transform.PackageScope

import java.text.SimpleDateFormat

/**
 * A rendered report payload: the exported bytes together with the metadata a distributor needs to attach or write
 * them (content type, file extension, a default file name).  Shared by all distributors so the export format is
 * resolved and rendered consistently.
 */
@PackageScope
class ReportPayload {

    private static final String FILE_NAME_TIMESTAMP_FORMAT = "yyyyMMdd-HHmmss"

    final byte[] bytes
    final String contentType
    final String fileExtension
    final String fileName

    private ReportPayload(byte[] bytes, String contentType, String fileExtension, String fileName) {
        this.bytes = bytes
        this.contentType = contentType
        this.fileExtension = fileExtension
        this.fileName = fileName
    }

    /**
     * Render the given report data using the exporter registered for {@code format}.
     *
     * @throws ReportException if no exporter is registered for the format
     */
    static ReportPayload render(ReportExporterRegistry exporterRegistry, String format, ReportExecution execution,
                                ReportData reportData) {
        ReportExporter exporter = exporterRegistry.getExporter(format)

        if (!exporter) {
            throw new ReportException("no report exporter registered for format : ${format}")
        }

        def outputStream = new ByteArrayOutputStream()

        exporter.export(reportData, outputStream)

        new ReportPayload(outputStream.toByteArray(), exporter.contentType, exporter.fileExtension,
                defaultFileName(execution, exporter.fileExtension))
    }

    private static String defaultFileName(ReportExecution execution, String fileExtension) {
        def timestampSource = execution.finishedAt ?: execution.startedAt
        def timestamp = new SimpleDateFormat(FILE_NAME_TIMESTAMP_FORMAT).format(
                (timestampSource ?: Calendar.instance).time)

        "${execution.reportName}-${timestamp}.${fileExtension}"
    }
}
