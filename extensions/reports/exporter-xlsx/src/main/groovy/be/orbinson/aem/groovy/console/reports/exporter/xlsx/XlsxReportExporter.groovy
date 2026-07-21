package be.orbinson.aem.groovy.console.reports.exporter.xlsx

import be.orbinson.aem.groovy.console.reports.ReportExporter
import be.orbinson.aem.groovy.console.reports.data.ReportColumnType
import be.orbinson.aem.groovy.console.reports.data.ReportData
import groovy.util.logging.Slf4j
import org.apache.poi.common.usermodel.HyperlinkType
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.CreationHelper
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.osgi.service.component.annotations.Component

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/**
 * XLSX exporter based on Apache POI's streaming workbook.  Column types map to typed cells: numbers and
 * booleans as native values, dates as date cells with a date format, links as hyperlinks.
 */
@Component(service = ReportExporter, immediate = true)
@Slf4j("LOG")
class XlsxReportExporter implements ReportExporter {

    private static final int ROW_ACCESS_WINDOW_SIZE = 100

    private static final String SHEET_NAME = "Report"

    private static final String DATE_CELL_FORMAT = "yyyy-mm-dd hh:mm:ss"

    // only these hyperlink schemes are written, mirroring the UI's isSafeHref; a bare/relative href is a
    // site-relative link and is allowed. Blocks file:, javascript:, data:, etc. from report data.
    private static final List<String> SAFE_LINK_SCHEMES = ["http", "https", "mailto"].asImmutable()

    @Override
    String getFormat() {
        "xlsx"
    }

    @Override
    String getContentType() {
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }

    @Override
    String getFileExtension() {
        "xlsx"
    }

    @Override
    void export(ReportData reportData, OutputStream outputStream) {
        def workbook = new SXSSFWorkbook(ROW_ACCESS_WINDOW_SIZE)

        try {
            def sheet = workbook.createSheet(SHEET_NAME)
            def creationHelper = workbook.creationHelper

            def headerStyle = workbook.createCellStyle()
            def headerFont = workbook.createFont()

            headerFont.bold = true
            headerStyle.setFont(headerFont)

            def dateStyle = workbook.createCellStyle()

            dateStyle.dataFormat = creationHelper.createDataFormat().getFormat(DATE_CELL_FORMAT)

            // UI-only columns (exported == false) are omitted from the export
            def columns = reportData.exportedColumns
            def headerRow = sheet.createRow(0)

            columns.eachWithIndex { column, index ->
                def cell = headerRow.createCell(index)

                cell.setCellValue(column.name ?: "")
                cell.cellStyle = headerStyle
            }

            reportData.exportedRows.eachWithIndex { row, rowIndex ->
                def sheetRow = sheet.createRow(rowIndex + 1)

                row.eachWithIndex { value, columnIndex ->
                    def cell = sheetRow.createCell(columnIndex)
                    def type = columnIndex < columns.size() ?
                            columns[columnIndex].type : ReportColumnType.STRING

                    setCellValue(cell, value, type, creationHelper, dateStyle)
                }
            }

            workbook.write(outputStream)
        } finally {
            workbook.dispose()
            workbook.close()
        }
    }

    // internals

    private static void setCellValue(Cell cell, Object value, ReportColumnType type,
                                     CreationHelper creationHelper, CellStyle dateStyle) {
        if (value == null) {
            return
        }

        switch (type) {
            case ReportColumnType.NUMBER:
                def number = toNumber(value)

                if (number != null) {
                    cell.setCellValue(number.doubleValue())
                } else {
                    cell.setCellValue(value as String)
                }

                break
            case ReportColumnType.BOOLEAN:
                cell.setCellValue(value instanceof Boolean ? value : Boolean.parseBoolean(value as String))

                break
            case ReportColumnType.DATE:
                def date = toDate(value)

                if (date != null) {
                    cell.setCellValue(date)
                    cell.cellStyle = dateStyle
                } else {
                    cell.setCellValue(value as String)
                }

                break
            case ReportColumnType.LINK:
                setLinkCellValue(cell, value, creationHelper)

                break
            default:
                cell.setCellValue(value as String)
        }
    }

    private static void setLinkCellValue(Cell cell, Object value, CreationHelper creationHelper) {
        def text = value as String
        def href = null

        if (value instanceof Map) {
            text = (value["text"] ?: value["href"]) as String
            href = value["href"] as String
        }

        cell.setCellValue(text ?: "")

        if (href && isSafeHref(href)) {
            try {
                def hyperlink = creationHelper.createHyperlink(HyperlinkType.URL)

                hyperlink.address = href

                cell.hyperlink = hyperlink
            } catch (IllegalArgumentException e) {
                LOG.debug("invalid hyperlink address : {}", href, e)
            }
        }
    }

    // a scheme-bearing href must use an allowed scheme; schemeless (site-relative) hrefs are allowed
    private static boolean isSafeHref(String href) {
        def matcher = (href =~ /(?i)^([a-z][a-z0-9+.\-]*):/)

        !matcher || SAFE_LINK_SCHEMES.contains(matcher.group(1).toLowerCase())
    }

    private static Number toNumber(Object value) {
        if (value instanceof Number) {
            return value
        }

        try {
            new BigDecimal(value as String)
        } catch (NumberFormatException ignored) {
            null
        }
    }

    private static Date toDate(Object value) {
        if (value instanceof Date) {
            return value
        }

        if (value instanceof Calendar) {
            return value.time
        }

        def text = value as String

        if (!text) {
            return null
        }

        // ISO-8601 instant with trailing Z (what ReportData writes), then bare local date-time, then date-only,
        // both interpreted as UTC
        try {
            return Date.from(Instant.parse(text))
        } catch (DateTimeParseException ignored) {
            // try next
        }

        try {
            return Date.from(LocalDateTime.parse(text).toInstant(ZoneOffset.UTC))
        } catch (DateTimeParseException ignored) {
            // try next
        }

        try {
            return Date.from(LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant())
        } catch (DateTimeParseException ignored) {
            null
        }
    }
}
