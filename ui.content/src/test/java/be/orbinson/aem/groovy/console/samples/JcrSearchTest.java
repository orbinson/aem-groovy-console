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
 * Tests the {@code JcrSearch.groovy} sample: builds an XPath query via the JCR QueryManager and prints the results.
 */
@ExtendWith(AemContextExtension.class)
class JcrSearchTest {

    private final AemContext context = new AemContextBuilder(ResourceResolverType.JCR_MOCK)
            .plugin(ContextPlugins.GROOVY_CONSOLE)
            .build();

    @Test
    void runsXPathQuery() {
        context.create().page("/content/we-retail", "template", "We Retail");
        context.create().page("/content/we-retail/bikes", "template", "Bike");

        RunScriptResponse response = GroovyConsole.runScript(context, Samples.read("JcrSearch.groovy"));

        assertTrue(response.getExceptionStackTrace().isEmpty(), response.getExceptionStackTrace());
    }
}
