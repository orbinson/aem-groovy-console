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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the {@code FindPagesWithProperty.groovy} sample: prints pages under we-retail that are not hidden in nav.
 */
@ExtendWith(AemContextExtension.class)
class FindPagesWithPropertyTest {

    private final AemContext context = new AemContextBuilder(ResourceResolverType.JCR_MOCK)
            .plugin(ContextPlugins.GROOVY_CONSOLE)
            .build();

    @Test
    void printsPagesShownInNavigation() {
        context.create().page("/content/we-retail", "template", "We Retail");
        context.create().page("/content/we-retail/men", "template", "Men");
        context.create().page("/content/we-retail/hidden", "template",
                Map.of(NameConstants.PN_HIDE_IN_NAV, true));

        RunScriptResponse response = GroovyConsole.runScript(context, Samples.read("FindPagesWithProperty.groovy"));

        assertTrue(response.getExceptionStackTrace().isEmpty(), response.getExceptionStackTrace());
        List<String> lines = response.getOutput().lines().collect(Collectors.toList());
        assertTrue(lines.contains("/content/we-retail"));
        assertTrue(lines.contains("/content/we-retail/men"));
        assertFalse(lines.contains("/content/we-retail/hidden"));
    }
}
