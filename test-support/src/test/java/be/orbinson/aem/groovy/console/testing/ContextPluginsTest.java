package be.orbinson.aem.groovy.console.testing;

import be.orbinson.aem.groovy.console.response.RunScriptResponse;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextBuilder;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test that uses {@link ContextPlugins#GROOVY_CONSOLE} on a plain {@code SlingContext}.
 * This module has no AEM/uber-jar dependency to showcase that everything works without the uber-jar dependency.
 */
@ExtendWith(SlingContextExtension.class)
class ContextPluginsTest {

    private final SlingContext context = new SlingContextBuilder()
            .plugin(ContextPlugins.GROOVY_CONSOLE)
            .build();

    @Test
    void runsAnInlineScriptAndCapturesOutput() {
        context.create().resource("/content/site", "jcr:title", "Site");

        RunScriptResponse response = GroovyConsole.runScript(context,
                "println resourceResolver.getResource('/content/site').getValueMap().get('jcr:title')");

        assertTrue(response.getExceptionStackTrace().isEmpty(), response.getExceptionStackTrace());
        assertEquals("Site\n", response.getOutput());
    }

}
