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
 * Tests the {@code FindReferences.groovy} sample: uses com.day.cq.wcm.commons.ReferenceSearch over the resolver and
 * builds a table of references. With no references present the search returns an empty result.
 */
@ExtendWith(AemContextExtension.class)
class FindReferencesTest {

    private final AemContext context = new AemContextBuilder(ResourceResolverType.JCR_MOCK)
            .plugin(ContextPlugins.GROOVY_CONSOLE)
            .build();

    @Test
    void runsReferenceSearch() {
        context.create().page("/content/we-retail", "template", "We Retail");

        RunScriptResponse response = GroovyConsole.runScript(context, Samples.read("FindReferences.groovy"));

        assertTrue(response.getExceptionStackTrace().isEmpty(), response.getExceptionStackTrace());
    }
}
