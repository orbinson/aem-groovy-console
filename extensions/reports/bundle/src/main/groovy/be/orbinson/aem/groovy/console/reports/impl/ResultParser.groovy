package be.orbinson.aem.groovy.console.reports.impl

import be.orbinson.aem.groovy.console.reports.data.ReportColumn
import be.orbinson.aem.groovy.console.reports.data.ReportColumnType
import be.orbinson.aem.groovy.console.reports.data.ReportData
import groovy.json.JsonSlurper

import static be.orbinson.aem.groovy.console.reports.constants.ReportsConstants.JSON_KEY_REPORT_DATA
import static be.orbinson.aem.groovy.console.reports.constants.ReportsConstants.JSON_KEY_TABLE

/**
 * Parses the (string) result of a console script execution back into report data.  Recognizes the typed
 * {@link ReportData} JSON envelope and the console's <code>Table</code> envelope; any other result is wrapped
 * as a single-cell text result.
 */
class ResultParser {

    static ReportData parse(String result) {
        if (result) {
            def parsed = tryParseJson(result)

            if (parsed instanceof Map) {
                if (parsed[JSON_KEY_REPORT_DATA] instanceof Map) {
                    return fromReportDataMap(parsed[JSON_KEY_REPORT_DATA] as Map)
                }

                if (parsed[JSON_KEY_TABLE] instanceof Map) {
                    return fromTableMap(parsed[JSON_KEY_TABLE] as Map)
                }
            }
        }

        fallback(result)
    }

    // internals

    private static Object tryParseJson(String result) {
        try {
            new JsonSlurper().parseText(result)
        } catch (Exception ignored) {
            null
        }
    }

    /** Build report data from a persisted/parsed {@code {columns, rows}} map. Shared with the result store. */
    static ReportData fromReportDataMap(Map map) {
        def reportData = new ReportData()

        (map["columns"] as List ?: []).each { column ->
            reportData.columns.add(new ReportColumn(
                    name: column["name"] as String,
                    type: toColumnType(column["type"] as String),
                    // exported defaults to true; only an explicit JSON false marks a UI-only column
                    exported: column["exported"] == null || column["exported"]
            ))
        }

        (map["rows"] as List ?: []).each { row ->
            reportData.rows.add(row as List)
        }

        normalizeRowSizes(reportData)
    }

    private static ReportData fromTableMap(Map map) {
        def reportData = new ReportData()

        (map["columns"] as List ?: []).each { column ->
            reportData.column(column as String)
        }

        (map["rows"] as List ?: []).each { row ->
            reportData.rows.add((row as List).collect { cell -> cell == null ? null : cell as String })
        }

        normalizeRowSizes(reportData)
    }

    private static ReportData fallback(String result) {
        def reportData = new ReportData()

        reportData.column("Result")

        if (result) {
            reportData.row(result)
        }

        reportData
    }

    private static ReportData normalizeRowSizes(ReportData reportData) {
        def columnCount = reportData.columns.size()

        reportData.rows = reportData.rows.collect { row ->
            if (row.size() == columnCount) {
                row
            } else if (row.size() > columnCount) {
                row.subList(0, columnCount)
            } else {
                row + ([null] * (columnCount - row.size()))
            }
        }

        reportData
    }

    private static ReportColumnType toColumnType(String type) {
        try {
            type ? ReportColumnType.valueOf(type.toUpperCase()) : ReportColumnType.STRING
        } catch (IllegalArgumentException ignored) {
            ReportColumnType.STRING
        }
    }

    private ResultParser() {

    }
}
