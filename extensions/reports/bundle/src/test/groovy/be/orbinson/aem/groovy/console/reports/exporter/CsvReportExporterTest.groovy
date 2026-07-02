package be.orbinson.aem.groovy.console.reports.exporter

import be.orbinson.aem.groovy.console.reports.data.ReportColumnType
import be.orbinson.aem.groovy.console.reports.data.ReportData
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue

class CsvReportExporterTest {

    private static final String BOM = "\uFEFF"

    private static String export(ReportData data, Locale locale = Locale.ENGLISH) {
        def out = new ByteArrayOutputStream()
        new CsvReportExporter().export(data, out, locale)
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
    void "uses a semicolon delimiter for comma-decimal locales and a comma otherwise"() {
        def data = new ReportData()
        data.column("A")
        data.column("B")
        data.row("1", "2")

        assertTrue(export(data, Locale.ENGLISH).contains("A,B"), "English locale uses a comma delimiter")
        assertTrue(export(data, new Locale("nl")).contains("A;B"), "comma-decimal locale uses a semicolon delimiter")
    }

    @Test
    void "quotes only on the active delimiter"() {
        def data = new ReportData()
        data.column("A")
        data.row("a;b")

        // ';' is a plain value under a comma delimiter, but must be quoted under a semicolon delimiter
        assertFalse(export(data, Locale.ENGLISH).contains('"a;b"'), "semicolon need not be quoted with comma delimiter")
        assertTrue(export(data, new Locale("nl")).contains('"a;b"'), "semicolon must be quoted with semicolon delimiter")
    }

    @Test
    void "formats NUMBER cells with the locale decimal separator and no grouping"() {
        def data = new ReportData()
        data.column("Amount", ReportColumnType.NUMBER)
        data.row(1234.5)

        assertTrue(export(data, Locale.ENGLISH).contains("1234.5"), "English locale keeps a dot decimal")
        assertTrue(export(data, new Locale("nl")).contains("1234,5"), "comma-decimal locale uses a comma decimal")
        assertFalse(export(data, Locale.ENGLISH).contains("1,234"), "no grouping separator that would clash with the delimiter")
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
