package be.orbinson.aem.groovy.console.reports.servlets

import be.orbinson.aem.groovy.console.reports.ReportExporter
import be.orbinson.aem.groovy.console.reports.model.ReportDefinition
import be.orbinson.aem.groovy.console.reports.model.ReportExecution
import be.orbinson.aem.groovy.console.reports.model.ReportParameter
import be.orbinson.aem.groovy.console.reports.model.ReportPreview
import be.orbinson.aem.groovy.console.reports.model.ReportResultPage

import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

import static be.orbinson.aem.groovy.console.reports.constants.ReportsConstants.DATE_FORMAT_ISO_8601

/**
 * Maps reports model objects to the JSON shapes of the HTTP API.
 */
class ReportJsonMapper {

    static Map summary(ReportDefinition definition, boolean canEdit) {
        [
                name       : definition.name,
                title      : definition.title,
                description: definition.description,
                category   : definition.category,
                canEdit    : canEdit
        ]
    }

    static Map definition(ReportDefinition definition, boolean canEdit, List<ReportExporter> exporters) {
        [
                name          : definition.name,
                title         : definition.title,
                description   : definition.description,
                category      : definition.category,
                script        : definition.script,
                pageSize      : definition.pageSize,
                parameters    : definition.parameters.collect { reportParameter -> parameter(reportParameter) },
                created       : formatDate(definition.created),
                createdBy     : definition.createdBy,
                lastModified  : formatDate(definition.lastModified),
                lastModifiedBy: definition.lastModifiedBy,
                canEdit       : canEdit,
                exportFormats : exporters.collect { exporter -> format(exporter) }
        ]
    }

    static Map parameter(ReportParameter parameter) {
        [
                name        : parameter.name,
                label       : parameter.label,
                type        : parameter.type.name(),
                defaultValue: parameter.defaultValue,
                required    : parameter.required,
                options     : parameter.options,
                pathType    : parameter.pathType,
                rootPath    : parameter.rootPath,
                order       : parameter.order
        ]
    }

    static Map execution(ReportExecution execution) {
        [
                executionId        : execution.id,
                reportName         : execution.reportName,
                status             : execution.status?.name(),
                userId             : execution.userId,
                startedAt          : formatDate(execution.startedAt),
                finishedAt         : formatDate(execution.finishedAt),
                durationMillis     : execution.durationMillis,
                runningTime        : execution.runningTime,
                rowCount           : execution.rowCount,
                columnCount        : execution.columnCount,
                parameterValues    : execution.parameterValues,
                output             : execution.output,
                exceptionStackTrace: execution.exceptionStackTrace
        ]
    }

    static Map resultPage(ReportResultPage resultPage) {
        [
                columns     : resultPage.columns.collect { column ->
                    [name: column.name, type: column.type.name(), exported: column.exported]
                },
                rows        : resultPage.rows,
                page        : resultPage.page,
                pageSize    : resultPage.pageSize,
                totalRows   : resultPage.totalRows,
                totalPages  : resultPage.totalPages,
                nextPage    : resultPage.nextPage,
                previousPage: resultPage.previousPage
        ]
    }

    static Map preview(ReportPreview preview) {
        [
                status             : preview.status?.name(),
                columns            : (preview.data?.columns ?: []).collect { column ->
                    [name: column.name, type: column.type.name(), exported: column.exported]
                },
                rows               : preview.data?.rows ?: [],
                rowCount           : preview.data?.rows?.size() ?: 0,
                output             : preview.output,
                exceptionStackTrace: preview.exceptionStackTrace,
                runningTime        : preview.runningTime
        ]
    }

    static Map format(ReportExporter exporter) {
        [
                format       : exporter.format,
                contentType  : exporter.contentType,
                fileExtension: exporter.fileExtension
        ]
    }

    private static final DateTimeFormatter ISO_8601 =
            DateTimeFormatter.ofPattern(DATE_FORMAT_ISO_8601).withZone(ZoneOffset.UTC)

    private static String formatDate(Calendar calendar) {
        calendar == null ? null : ISO_8601.format(calendar.toInstant())
    }

    private ReportJsonMapper() {

    }
}
