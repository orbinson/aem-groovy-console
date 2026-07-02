package be.orbinson.aem.groovy.console.reports.data

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue

class ReportDataTest {

    @Test
    void "column defaults to exported and the overload sets the flag"() {
        def data = new ReportData()

        data.column("A")
        data.column("B", ReportColumnType.STRING, false)

        assertTrue(data.columns[0].exported)
        assertFalse(data.columns[1].exported)
    }

    @Test
    void "exported columns and rows exclude UI-only columns while the full data is preserved"() {
        def data = new ReportData()

        data.column("Name")
        data.column("Edit", ReportColumnType.LINK, false)
        data.column("Count", ReportColumnType.NUMBER)

        data.row("a", [text: "edit", href: "/a"], 1)
        data.row("b", [text: "edit", href: "/b"], 2)

        // export view drops the UI-only column and the matching cells
        assertEquals(["Name", "Count"], data.exportedColumns*.name)
        assertEquals([["a", 1], ["b", 2]], data.exportedRows)

        // the UI still sees every column and cell
        assertEquals(3, data.columns.size())
        assertEquals(3, data.rows[0].size())
    }

    @Test
    void "toMap carries the exported flag for each column"() {
        def data = new ReportData()

        data.column("A")
        data.column("B", ReportColumnType.STRING, false)

        def map = data.toMap()

        assertEquals(true, map.columns[0].exported)
        assertEquals(false, map.columns[1].exported)
    }
}
