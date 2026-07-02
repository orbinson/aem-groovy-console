package be.orbinson.aem.groovy.console.reports.impl

import be.orbinson.aem.groovy.console.reports.data.ReportColumnType
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue

class ResultParserTest {

    @Test
    void "parses the typed reportData envelope including the exported flag"() {
        def json = '{"reportData":{"columns":[' +
                '{"name":"Name","type":"STRING","exported":true},' +
                '{"name":"Edit","type":"LINK","exported":false}],' +
                '"rows":[["a",{"text":"e","href":"/x"}]]}}'

        def data = ResultParser.parse(json)

        assertEquals(["Name", "Edit"], data.columns*.name)
        assertEquals(ReportColumnType.LINK, data.columns[1].type)
        assertTrue(data.columns[0].exported)
        assertFalse(data.columns[1].exported, "exported=false must round-trip")
        assertEquals(1, data.rows.size())
    }

    @Test
    void "parses the console Table envelope as string columns"() {
        def json = '{"table":{"columns":["A","B"],"rows":[["1","2"],["3","4"]]}}'

        def data = ResultParser.parse(json)

        assertEquals(["A", "B"], data.columns*.name)
        assertEquals(ReportColumnType.STRING, data.columns[0].type)
        assertEquals(2, data.rows.size())
    }

    @Test
    void "wraps a non-envelope result as a single text cell"() {
        def data = ResultParser.parse("just some output")

        assertEquals(["Result"], data.columns*.name)
        assertEquals([["just some output"]], data.rows)
    }

    @Test
    void "falls back for invalid JSON and handles null or empty"() {
        def invalid = ResultParser.parse("{not valid json")
        assertEquals(["Result"], invalid.columns*.name)

        def empty = ResultParser.parse("")
        assertEquals(["Result"], empty.columns*.name)
        assertTrue(empty.rows.isEmpty(), "empty result should have no rows")
    }

    @Test
    void "normalizes ragged rows to the column count"() {
        def json = '{"reportData":{"columns":[' +
                '{"name":"A","type":"STRING"},{"name":"B","type":"STRING"}],' +
                '"rows":[["only-one"],["a","b","extra"]]}}'

        def data = ResultParser.parse(json)

        assertEquals(["only-one", null], data.rows[0], "short row padded with null to the column count")
        assertEquals(["a", "b"], data.rows[1], "long row truncated to the column count")
    }
}
