package be.orbinson.aem.groovy.console.reports.data

import groovy.json.JsonBuilder

import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Typed tabular result for report scripts.  Report scripts should return an instance of this class (typically
 * built via the <code>report.data()</code> binding) to get typed cells in the reports UI and exports.
 *
 * <pre>
 * def data = report.data()
 *
 * data.column('Page', ReportColumnType.LINK)
 * data.column('Views', ReportColumnType.NUMBER)
 * data.column('Modified', ReportColumnType.DATE)
 *
 * data.row([text: 'My Page', href: '/content/my-page.html'], 42, new Date())
 *
 * data
 * </pre>
 *
 * The console serializes script results to a String, so this class emits a recognizable JSON envelope from
 * {@link #toString()} which the reports bundle parses back after execution.
 */
class ReportData {

    /** Key of the JSON envelope emitted by {@link #toString()}. */
    public static final String JSON_ENVELOPE_KEY = "reportData"

    private static final String DATE_FORMAT_ISO_8601 = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

    private static final DateTimeFormatter ISO_8601 =
            DateTimeFormatter.ofPattern(DATE_FORMAT_ISO_8601).withZone(ZoneOffset.UTC)

    List<ReportColumn> columns = []

    List<List<Object>> rows = []

    /**
     * Add a column.
     *
     * @param name column display name
     * @param type column type, defaults to {@link ReportColumnType#STRING}
     * @param exported whether the column is included in CSV/XLSX exports; pass false for UI-only columns
     *                 (e.g. an edit link). Defaults to true.
     */
    void column(String name, ReportColumnType type = ReportColumnType.STRING, boolean exported = true) {
        assert !rows, "columns must be defined before adding rows"

        columns.add(new ReportColumn(name: name, type: type, exported: exported))
    }

    /**
     * Add a row.  The number of values must match the number of columns.  Values are normalized according to
     * their column type (e.g. dates are formatted as ISO-8601 strings).
     *
     * @param values cell values
     */
    void row(Object... values) {
        row(values as List)
    }

    /**
     * Add a row.  The number of values must match the number of columns.
     *
     * @param values cell values
     */
    void row(List<Object> values) {
        assert columns, "columns must be defined before adding a row"
        assert values.size() == columns.size(), "row data size does not match number of columns"

        rows.add((0..<values.size()).collect { index -> normalizeCell(values[index], columns[index].type) })
    }

    /**
     * Add multiple rows.
     *
     * @param rowValues list of rows
     */
    void rows(List<List<Object>> rowValues) {
        rowValues.each { values -> row(values) }
    }

    /**
     * Get this report data as a map suitable for JSON serialization.
     *
     * @return map with <code>columns</code> and <code>rows</code> keys
     */
    Map toMap() {
        [
                columns: columns.collect { column ->
                    [name: column.name, type: column.type.name(), exported: column.exported]
                },
                rows   : rows
        ]
    }

    /**
     * Columns flagged for export (i.e. not UI-only).
     *
     * @return exported columns
     */
    List<ReportColumn> getExportedColumns() {
        columns.findAll { column -> column.exported }
    }

    /**
     * Rows projected to only the {@link #getExportedColumns() exported columns}, preserving column order.
     *
     * @return rows with UI-only column cells removed
     */
    List<List<Object>> getExportedRows() {
        def indices = (0..<columns.size()).findAll { index -> columns[index].exported }

        rows.collect { row -> indices.collect { index -> index < row.size() ? row[index] : null } }
    }

    @Override
    String toString() {
        new JsonBuilder([(JSON_ENVELOPE_KEY): toMap()]).toString()
    }

    private static Object normalizeCell(Object value, ReportColumnType type) {
        if (value == null) {
            return null
        }

        switch (type) {
            case ReportColumnType.NUMBER:
                return value instanceof Number ? value : new BigDecimal(value as String)
            case ReportColumnType.BOOLEAN:
                return value instanceof Boolean ? value : Boolean.parseBoolean(value as String)
            case ReportColumnType.DATE:
                return formatDate(value)
            case ReportColumnType.LINK:
                if (value instanceof Map) {
                    return [text: (value["text"] ?: value["href"]) as String, href: value["href"] as String]
                }

                return [text: value as String, href: value as String]
            default:
                return value as String
        }
    }

    private static String formatDate(Object value) {
        def instant = null

        if (value instanceof Calendar) {
            instant = value.toInstant()
        } else if (value instanceof Date) {
            instant = value.toInstant()
        }

        instant != null ? ISO_8601.format(instant) : value as String
    }
}
