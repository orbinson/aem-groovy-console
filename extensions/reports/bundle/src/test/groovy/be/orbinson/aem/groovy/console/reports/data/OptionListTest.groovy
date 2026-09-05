package be.orbinson.aem.groovy.console.reports.data

import groovy.json.JsonSlurper
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals

class OptionListTest {

    @Test
    void "add stores value and label, and a single-arg add uses the value as the label"() {
        def options = new OptionList()

        options.add("a", "Apple")
        options.add("b")

        assertEquals([[value: "a", label: "Apple"], [value: "b", label: "b"]], options.toList())
    }

    @Test
    void "toString emits the options JSON envelope parsed back by the reports bundle"() {
        def options = new OptionList()

        options.add("/content/a", "Page A")

        def parsed = new JsonSlurper().parseText(options.toString())

        assertEquals([[value: "/content/a", label: "Page A"]], parsed[OptionList.JSON_ENVELOPE_KEY])
    }
}
