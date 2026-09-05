package be.orbinson.aem.groovy.console.samples;

import be.orbinson.aem.groovy.console.response.RunScriptResponse;
import be.orbinson.aem.groovy.console.testing.ContextPlugins;
import be.orbinson.aem.groovy.console.testing.GroovyConsole;
import com.day.cq.wcm.api.NameConstants;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextBuilder;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the {@code UpdatePageTitles.groovy} sample. It reads and writes JCR content (getPage/recurse/node/set/save)
 */
@ExtendWith(AemContextExtension.class)
class UpdatePageTitlesTest {

    private final AemContext context = new AemContextBuilder(ResourceResolverType.JCR_MOCK)
            .plugin(ContextPlugins.GROOVY_CONSOLE)
            .build();

    @Test
    void rewritesPageTitlesUnderWeRetail() {
        context.create().page("/content/we-retail", "template", "We Retail");
        context.create().page("/content/we-retail/men", "template", "Men");

        RunScriptResponse response = GroovyConsole.runScript(context, Samples.read("UpdatePageTitles.groovy"));

        assertTrue(response.getExceptionStackTrace().isEmpty(), response.getExceptionStackTrace());
        assertEquals("We Retail | We Retail", pageTitle("/content/we-retail"));
        assertEquals("Men | We Retail", pageTitle("/content/we-retail/men"));
    }

    private String pageTitle(String pagePath) {
        return context.resourceResolver()
                .getResource(pagePath + "/jcr:content")
                .getValueMap()
                .get(NameConstants.PN_PAGE_TITLE, String.class);
    }
}
