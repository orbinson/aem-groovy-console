package be.orbinson.aem.groovy.console.reports.exporter

import be.orbinson.aem.groovy.console.reports.LocaleAwareReportExporter
import be.orbinson.aem.groovy.console.reports.ReportExporter
import be.orbinson.aem.groovy.console.reports.data.ReportColumnType
import be.orbinson.aem.groovy.console.reports.data.ReportData
import org.osgi.service.component.annotations.Component

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

import static be.orbinson.aem.groovy.console.reports.constants.ReportsConstants.CHARSET

/**
 * RFC 4180 compliant CSV exporter.  Writes a UTF-8 BOM so Excel detects the encoding.  The field delimiter is
 * locale-aware: locales whose decimal separator is a comma (nl, de, fr, \u2026) get ';' so Excel still splits into
 * columns, others get ','.  NUMBER cells are formatted with the locale's decimal separator (grouping disabled,
 * so the number never clashes with the delimiter) so Excel recognizes them as numeric in that locale.
 */
@Component(service = ReportExporter, immediate = true)
class CsvReportExporter implements LocaleAwareReportExporter {

    private static final String BOM = "\uFEFF"

    private static final String CRLF = "\r\n"

    @Override
    String getFormat() {
        "csv"
    }

    @Override
    String getContentType() {
        "text/csv"
    }

    @Override
    String getFileExtension() {
        "csv"
    }

    @Override
    void export(ReportData reportData, OutputStream outputStream) {
        export(reportData, outputStream, Locale.ENGLISH)
    }

    @Override
    void export(ReportData reportData, OutputStream outputStream, Locale locale) {
        def effectiveLocale = locale ?: Locale.ENGLISH
        def delimiter = delimiterFor(effectiveLocale)
        def numberFormat = numberFormat(effectiveLocale)
        def writer = new OutputStreamWriter(outputStream, CHARSET)

        writer.write(BOM)

        // UI-only columns (exported == false) are omitted from the export
        def columns = reportData.exportedColumns

        writeRow(writer, columns*.name, delimiter)

        reportData.exportedRows.each { row ->
            def values = (0..<row.size()).collect { index ->
                def type = index < columns.size() ? columns[index].type : ReportColumnType.STRING

                cellToString(row[index], type, numberFormat)
            }

            writeRow(writer, values, delimiter)
        }

        writer.flush()
    }

    // internals

    // Excel opens CSV using the active locale's list separator: comma-decimal locales need ';' for columns.
    private static String delimiterFor(Locale locale) {
        new DecimalFormatSymbols(locale ?: Locale.ENGLISH).decimalSeparator == ',' as char ? ";" : ","
    }

    // Locale decimal separator, no grouping (grouping separators would clash with the field delimiter and add
    // spurious quoting), full precision (no rounding).
    private static DecimalFormat numberFormat(Locale locale) {
        def format = new DecimalFormat("0", DecimalFormatSymbols.getInstance(locale))

        format.groupingUsed = false
        format.maximumFractionDigits = 340

        format
    }

    private static void writeRow(Writer writer, List<String> values, String delimiter) {
        writer.write(values.collect { value -> quote(value, delimiter) }.join(delimiter))
        writer.write(CRLF)
    }

    private static String quote(String value, String delimiter) {
        if (value == null) {
            return ""
        }

        def sanitized = neutralizeFormula(value)

        if (sanitized.contains(delimiter) || sanitized.contains("\"") || sanitized.contains("\n") || sanitized.contains("\r")) {
            return "\"${sanitized.replace("\"", "\"\"")}\""
        }

        sanitized
    }

    /**
     * Prevent CSV formula/command injection: spreadsheet apps treat a cell that begins with =, +, -, @ (or a
     * leading tab/CR) as a formula, so a value like <code>=cmd|'/c ...'!A1</code> from report data would execute
     * on open.  Prefixing a single quote forces the cell to be read as text.
     */
    private static String neutralizeFormula(String value) {
        if (value && (value[0] in ["=", "+", "-", "@", "\t", "\r"])) {
            return "'" + value
        }

        value
    }

    private static String cellToString(Object cell, ReportColumnType type, DecimalFormat numberFormat) {
        if (cell == null) {
            return null
        }

        if (type == ReportColumnType.NUMBER && cell instanceof Number) {
            return numberFormat.format(cell)
        }

        if (cell instanceof Map) {
            def text = cell["text"] as String
            def href = cell["href"] as String

            if (text && href && text != href) {
                return "$text ($href)"
            }

            return href ?: text
        }

        cell as String
    }
}
