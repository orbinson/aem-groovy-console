package be.orbinson.aem.groovy.console.reports.exporter.xlsx

import be.orbinson.aem.groovy.console.reports.data.ReportColumnType
import be.orbinson.aem.groovy.console.reports.data.ReportData
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertTrue

class XlsxReportExporterTest {

    @Test
    void "exports typed cells and excludes UI-only columns"() {
        def data = new ReportData()
        data.column("Name")
        data.column("Count", ReportColumnType.NUMBER)
        data.column("Active", ReportColumnType.BOOLEAN)
        data.column("Page", ReportColumnType.LINK)
        data.column("Internal", ReportColumnType.STRING, false) // UI-only: must not appear in the export
        data.row("alpha", 10, true, [text: "Home", href: "https://example.com/p"], "secret")

        def out = new ByteArrayOutputStream()
        new XlsxReportExporter().export(data, out)

        WorkbookFactory.create(new ByteArrayInputStream(out.toByteArray())).withCloseable { workbook ->
            def sheet = workbook.getSheetAt(0)

            def header = sheet.getRow(0)
            assertEquals(4, header.physicalNumberOfCells, "UI-only column must be excluded from the header")
            assertEquals("Name", header.getCell(0).stringCellValue)
            assertEquals("Count", header.getCell(1).stringCellValue)
            assertEquals("Active", header.getCell(2).stringCellValue)
            assertEquals("Page", header.getCell(3).stringCellValue)

            def row = sheet.getRow(1)
            assertEquals(4, row.physicalNumberOfCells, "UI-only column value must be excluded")
            assertEquals("alpha", row.getCell(0).stringCellValue)
            assertEquals(CellType.NUMERIC, row.getCell(1).cellType)
            assertEquals(10.0d, row.getCell(1).numericCellValue)
            assertEquals(CellType.BOOLEAN, row.getCell(2).cellType)
            assertTrue(row.getCell(2).booleanCellValue)
            assertEquals("Home", row.getCell(3).stringCellValue)
            assertNotNull(row.getCell(3).hyperlink, "LINK cell should carry a hyperlink")
            assertEquals("https://example.com/p", row.getCell(3).hyperlink.address)
        }
    }
}
