package be.orbinson.aem.groovy.console.reports.samples;

import be.orbinson.aem.groovy.console.reports.extension.ReportBindingExtensionProvider;
import be.orbinson.aem.groovy.console.response.RunScriptResponse;
import be.orbinson.aem.groovy.console.testing.ContextPlugins;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextBuilder;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the shipped {@code sample-content-listing} report script: it lists the child resources of a content path.
 */
@ExtendWith(SlingContextExtension.class)
class SampleContentListingTest {

    // register the reports' binding provider before the console starts so `report` and `params` resolve
    private final SlingContext context = new SlingContextBuilder()
            .beforeSetUp(ctx -> ctx.registerInjectActivateService(new ReportBindingExtensionProvider()))
            .plugin(ContextPlugins.GROOVY_CONSOLE)
            .build();

    @Test
    void listsChildResources() {
        context.create().resource("/content/site");
        context.create().resource("/content/site/en", "sling:resourceType", "app/components/page");
        context.create().resource("/content/site/fr", "sling:resourceType", "app/components/page");

        RunScriptResponse response = ReportConsole.runReport(context,
                Reports.readScript("sample-content-listing"), Map.of("path", "/content/site"));

        assertTrue(response.getExceptionStackTrace().isEmpty(), response.getExceptionStackTrace());

        // the script returns ReportData, which the console serializes as a { "reportData": { columns, rows } } envelope
        String result = response.getResult();
        assertTrue(result.contains("reportData"), "expected a reportData envelope but was: [" + result + "]");
        assertTrue(result.contains("/content/site/en") && result.contains("/content/site/fr"),
                "expected both child paths in the result but was: [" + result + "]");
        assertTrue(result.contains("app/components/page"),
                "expected the child resource type in the result but was: [" + result + "]");
    }
}
