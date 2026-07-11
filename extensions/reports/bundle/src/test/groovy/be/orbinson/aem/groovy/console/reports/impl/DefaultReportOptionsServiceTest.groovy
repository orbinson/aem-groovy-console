package be.orbinson.aem.groovy.console.reports.impl

import be.orbinson.aem.groovy.console.reports.ReportException
import be.orbinson.aem.groovy.console.reports.data.OptionList
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue

class DefaultReportOptionsServiceTest {

    @Test
    void "parses the options envelope into value and label pairs"() {
        def options = new OptionList()
        options.add("a", "Apple")
        options.add("b")

        def parsed = DefaultReportOptionsService.parseOptions(options.toString())

        assertEquals([[value: "a", label: "Apple"], [value: "b", label: "b"]], parsed)
    }

    @Test
    void "treats a plain string element as both value and label"() {
        def parsed = DefaultReportOptionsService.parseOptions('{"options":["x"]}')

        assertEquals([[value: "x", label: "x"]], parsed)
    }

    @Test
    void "returns an empty list for a blank result"() {
        assertTrue(DefaultReportOptionsService.parseOptions(null).isEmpty())
        assertTrue(DefaultReportOptionsService.parseOptions("").isEmpty())
    }

    @Test
    void "rejects a result that is not the options envelope"() {
        assertThrows(ReportException) {
            DefaultReportOptionsService.parseOptions("not json")
        }

        assertThrows(ReportException) {
            DefaultReportOptionsService.parseOptions('{"reportData":{}}')
        }
    }
}
