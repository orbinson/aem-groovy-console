package be.orbinson.aem.groovy.console.reports.impl

import be.orbinson.aem.groovy.console.reports.ReportService
import be.orbinson.aem.groovy.console.reports.model.ReportDefinition
import be.orbinson.aem.groovy.console.reports.model.ReportParameter
import be.orbinson.aem.groovy.console.reports.model.ReportParameterType
import io.wcm.testing.mock.aem.junit5.AemContext
import io.wcm.testing.mock.aem.junit5.AemContextExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

import static org.junit.jupiter.api.Assertions.assertEquals

@ExtendWith(AemContextExtension.class)
class DefaultReportServiceTest {

    private final AemContext context = new AemContext()

    private ReportService reportService

    @BeforeEach
    void beforeEach() {
        reportService = context.registerInjectActivateService(new DefaultReportService())

        reportService.saveReport(context.resourceResolver(), new ReportDefinition(
                name: "sample",
                title: "Original title",
                description: "Original description",
                script: "return report.data()",
                parameters: [
                        new ReportParameter(name: "country", type: ReportParameterType.DYNAMIC,
                                optionsScript: "options.add('be')")
                ]), "author")
    }

    @Test
    void "saveReport round-trips the script and a dynamic parameter's options script"() {
        def definition = reportService.getReport(context.resourceResolver(), "sample")

        assertEquals("return report.data()", definition.script)
        assertEquals("options.add('be')", definition.parameters[0].optionsScript)
    }

    @Test
    void "updateReportMetadata changes only metadata, leaving the scripts and parameters untouched"() {
        reportService.updateReportMetadata(context.resourceResolver(), new ReportDefinition(
                name: "sample",
                title: "Updated by business user",
                description: "New description",
                // a business user cannot smuggle in a script through the metadata update
                script: "Runtime.runtime.exec('rm -rf /')",
                parameters: [
                        new ReportParameter(name: "country", type: ReportParameterType.DYNAMIC,
                                optionsScript: "Runtime.runtime.exec('evil')")
                ]), "business")

        def definition = reportService.getReport(context.resourceResolver(), "sample")

        // metadata is updated
        assertEquals("Updated by business user", definition.title)
        assertEquals("New description", definition.description)
        assertEquals("business", definition.lastModifiedBy)

        // executable Groovy and parameters are untouched
        assertEquals("return report.data()", definition.script)
        assertEquals(1, definition.parameters.size())
        assertEquals("options.add('be')", definition.parameters[0].optionsScript)
    }
}
