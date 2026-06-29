package be.orbinson.aem.groovy.console.reports.exporter

import be.orbinson.aem.groovy.console.reports.ReportExporter
import be.orbinson.aem.groovy.console.reports.data.ReportData
import org.osgi.service.component.annotations.Component

import static be.orbinson.aem.groovy.console.reports.constants.ReportsConstants.CHARSET

/**
 * RFC 4180 compliant CSV exporter.  Writes a UTF-8 BOM so Excel detects the encoding.
 */
@Component(service = ReportExporter, immediate = true)
class CsvReportExporter implements ReportExporter {

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
        def writer = new OutputStreamWriter(outputStream, CHARSET)

        writer.write(BOM)

        // UI-only columns (exported == false) are omitted from the export
        writeRow(writer, reportData.exportedColumns*.name)

        reportData.exportedRows.each { row ->
            writeRow(writer, row.collect { cell -> cellToString(cell) })
        }

        writer.flush()
    }

    // internals

    private static void writeRow(Writer writer, List<String> values) {
        writer.write(values.collect { value -> quote(value) }.join(","))
        writer.write(CRLF)
    }

    private static String quote(String value) {
        if (value == null) {
            return ""
        }

        def sanitized = neutralizeFormula(value)

        if (sanitized.contains(",") || sanitized.contains("\"") || sanitized.contains("\n") || sanitized.contains("\r")) {
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

    private static String cellToString(Object cell) {
        if (cell == null) {
            return null
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
