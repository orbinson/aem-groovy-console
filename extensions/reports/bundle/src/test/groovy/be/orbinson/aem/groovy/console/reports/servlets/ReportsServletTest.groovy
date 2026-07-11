package be.orbinson.aem.groovy.console.reports.servlets

import be.orbinson.aem.groovy.console.reports.model.ReportDefinition
import be.orbinson.aem.groovy.console.reports.model.ReportParameter
import be.orbinson.aem.groovy.console.reports.model.ReportParameterType
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNull

class ReportsServletTest {

    @Test
    void "preserveScripts keeps the existing report and dynamic option scripts, ignoring submitted changes"() {
        def existing = new ReportDefinition(
                name: "r",
                script: "return report.data()",
                parameters: [
                        new ReportParameter(name: "country", type: ReportParameterType.DYNAMIC,
                                optionsScript: "options.add('be')"),
                        new ReportParameter(name: "note", type: ReportParameterType.STRING)
                ])

        // a non-console caller edits metadata and tries to inject executable Groovy
        def submitted = new ReportDefinition(
                name: "r",
                description: "updated by business user",
                script: "Runtime.runtime.exec('rm -rf /')",
                parameters: [
                        new ReportParameter(name: "country", type: ReportParameterType.DYNAMIC,
                                optionsScript: "Runtime.runtime.exec('evil')"),
                        new ReportParameter(name: "note", type: ReportParameterType.STRING),
                        // a newly added dynamic parameter must not smuggle in a script either
                        new ReportParameter(name: "fresh", type: ReportParameterType.DYNAMIC,
                                optionsScript: "Runtime.runtime.exec('also evil')")
                ])

        ReportsServlet.preserveScripts(submitted, existing)

        // metadata edit is preserved
        assertEquals("updated by business user", submitted.description)
        // executable Groovy is reverted to the vetted, previously-stored content
        assertEquals("return report.data()", submitted.script)
        assertEquals("options.add('be')", submitted.parameters[0].optionsScript)
        // a brand-new dynamic parameter gets no script (nothing to carry forward)
        assertNull(submitted.parameters[2].optionsScript)
    }
}
