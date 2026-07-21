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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the {@code FindPagesWithTemplate.groovy} sample: prints pages whose cq:template matches a specific template.
 */
@ExtendWith(AemContextExtension.class)
class FindPagesWithTemplateTest {

    private static final String TEMPLATE = "/conf/we-retail/settings/wcm/templates/section-page";

    private final AemContext context = new AemContextBuilder(ResourceResolverType.JCR_MOCK)
            .plugin(ContextPlugins.GROOVY_CONSOLE)
            .build();

    @Test
    void printsPagesUsingTheTemplate() {
        context.create().page("/content/we-retail", "otherTemplate", "We Retail");
        // The 2nd arg becomes the cq:template property on jcr:content.
        context.create().page("/content/we-retail/products", TEMPLATE, "Products");

        RunScriptResponse response = GroovyConsole.runScript(context, Samples.read("FindPagesWithTemplate.groovy"));

        assertTrue(response.getExceptionStackTrace().isEmpty(), response.getExceptionStackTrace());
        assertEquals("/content/we-retail/products" + System.lineSeparator(), response.getOutput());
    }
}
