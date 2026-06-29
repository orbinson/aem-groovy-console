package be.orbinson.aem.groovy.console.reports.impl

import be.orbinson.aem.groovy.console.reports.model.ReportParameter
import be.orbinson.aem.groovy.console.reports.model.ReportParameterType
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertNull
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue

class ParameterCoercerTest {

    private static ReportParameter param(String name, ReportParameterType type, Map opts = [:]) {
        new ReportParameter(
                name: name,
                type: type,
                required: opts.required ?: false,
                defaultValue: opts.defaultValue as String,
                options: (opts.options ?: []) as List<String>)
    }

    @Test
    void "NUMBER coerces to BigDecimal and rejects invalid input"() {
        assertEquals(new BigDecimal("42.5"),
                ParameterCoercer.coerce([param("n", ReportParameterType.NUMBER)], [n: "42.5"]).n)

        assertThrows(IllegalArgumentException) {
            ParameterCoercer.coerce([param("n", ReportParameterType.NUMBER)], [n: "abc"])
        }
    }

    @Test
    void "BOOLEAN parses true and false"() {
        assertEquals(true, ParameterCoercer.coerce([param("b", ReportParameterType.BOOLEAN)], [b: "true"]).b)
        assertEquals(false, ParameterCoercer.coerce([param("b", ReportParameterType.BOOLEAN)], [b: "nope"]).b)
    }

    @Test
    void "DATE parses ISO formats and rejects invalid"() {
        assertTrue(ParameterCoercer.coerce([param("d", ReportParameterType.DATE)], [d: "2026-06-04"]).d instanceof Date)
        assertTrue(ParameterCoercer.coerce([param("d", ReportParameterType.DATE)],
                [d: "2026-06-04T10:15:30Z"]).d instanceof Date)

        assertThrows(IllegalArgumentException) {
            ParameterCoercer.coerce([param("d", ReportParameterType.DATE)], [d: "not-a-date"])
        }
    }

    @Test
    void "SELECT validates against options"() {
        def parameter = param("s", ReportParameterType.SELECT, [options: ["a", "b"]])

        assertEquals("a", ParameterCoercer.coerce([parameter], [s: "a"]).s)

        assertThrows(IllegalArgumentException) {
            ParameterCoercer.coerce([parameter], [s: "c"])
        }
    }

    @Test
    void "applies defaultValue, allows optional empty, enforces required"() {
        assertEquals("fallback", ParameterCoercer.coerce(
                [param("x", ReportParameterType.STRING, [defaultValue: "fallback"])], [:]).x)

        assertNull(ParameterCoercer.coerce([param("o", ReportParameterType.STRING)], [:]).o)

        assertThrows(IllegalArgumentException) {
            ParameterCoercer.coerce([param("r", ReportParameterType.STRING, [required: true])], [:])
        }
    }
}
