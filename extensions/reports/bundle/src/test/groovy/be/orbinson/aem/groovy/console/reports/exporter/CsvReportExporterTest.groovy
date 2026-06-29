package be.orbinson.aem.groovy.console.reports.exporter

import be.orbinson.aem.groovy.console.reports.data.ReportColumnType
import be.orbinson.aem.groovy.console.reports.data.ReportData
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue

class CsvReportExporterTest {

    private static final String BOM = "\uFEFF"

    private static String export(ReportData data) {
        def out = new ByteArrayOutputStream()
        new CsvReportExporter().export(data, out)
        new String(out.toByteArray(), "UTF-8")
    }

    @Test
    void "quotes comma and quote values per RFC 4180 and writes a BOM and CRLF"() {
        def data = new ReportData()
        data.column("A")
        data.column("B")
        data.row("a,b", 'he said "hi"')

        def csv = export(data)

        assertTrue(csv.startsWith(BOM), "expected a UTF-8 BOM")
        assertTrue(csv.contains('"a,b"'), "comma value should be quoted")
        assertTrue(csv.contains('"he said ""hi"""'), "embedded quotes should be doubled")
        assertTrue(csv.contains("\r\n"), "rows should be CRLF terminated")
    }

    @Test
    void "neutralizes spreadsheet formula injection"() {
        def data = new ReportData()
        data.column("A")
        data.row("=1+1")

        def csv = export(data)

        assertTrue(csv.contains("'=1+1"), "a leading = must be prefixed with an apostrophe")
        assertFalse(((csv =~ /(^|\n)=1\+1/) as boolean), "a raw formula must never start a cell")
    }

    @Test
    void "excludes UI-only columns from the export"() {
        def data = new ReportData()
        data.column("Name")
        data.column("Edit", ReportColumnType.LINK, false)
        data.row("alpha", [text: "e", href: "/x"])

        def csv = export(data)

        assertTrue(csv.contains("Name"))
        assertFalse(csv.contains("Edit"), "exported=false column must be omitted")
    }
}
