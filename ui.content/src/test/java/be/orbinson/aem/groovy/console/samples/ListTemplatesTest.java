package be.orbinson.aem.groovy.console.samples;

import be.orbinson.aem.groovy.console.response.RunScriptResponse;
import be.orbinson.aem.groovy.console.testing.ContextPlugins;
import be.orbinson.aem.groovy.console.testing.GroovyConsole;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextBuilder;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the {@code ListTemplates.groovy} sample: recurses pages and prints the distinct template paths in use.
 */
@ExtendWith(AemContextExtension.class)
class ListTemplatesTest {

    private static final String TEMPLATE = "/conf/we-retail/settings/wcm/templates/section-page";

    private final AemContext context = new AemContextBuilder(ResourceResolverType.JCR_MOCK)
            .plugin(ContextPlugins.GROOVY_CONSOLE)
            .build();

    @Test
    void printsTemplatesInUse() {
        context.create().resource(TEMPLATE, "jcr:primaryType", "cq:Template");
        context.create().page("/content/we-retail", TEMPLATE, "We Retail");
        context.create().page("/content/we-retail/men", TEMPLATE, "Men");

        RunScriptResponse response = GroovyConsole.runScript(context, Samples.read("ListTemplates.groovy"));

        assertTrue(response.getExceptionStackTrace().isEmpty(), response.getExceptionStackTrace());
        assertTrue(response.getOutput().contains(TEMPLATE),
                "expected the template path in the output but was: [" + response.getOutput() + "]");
    }
}
