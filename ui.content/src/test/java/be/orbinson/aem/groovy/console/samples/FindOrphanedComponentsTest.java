package be.orbinson.aem.groovy.console.samples;

import be.orbinson.aem.groovy.console.response.RunScriptResponse;
import be.orbinson.aem.groovy.console.testing.ContextPlugins;
import be.orbinson.aem.groovy.console.testing.GroovyConsole;
import com.day.cq.wcm.api.components.ComponentManager;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextBuilder;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the {@code FindOrphanedComponents.groovy} sample: recurses content and tables nodes whose sling:resourceType
 * is not a registered component.
 * <p>
 * aem-mock's {@code ComponentManager.getComponents()} throws {@code UnsupportedOperationException}, so this test
 * registers a mock ComponentManager as the adapter for {@code resourceResolver.adaptTo(ComponentManager)}.
 */
@ExtendWith(AemContextExtension.class)
class FindOrphanedComponentsTest {

    private final ComponentManager componentManager = mock(ComponentManager.class);

    private final AemContext context = new AemContextBuilder(ResourceResolverType.JCR_MOCK)
            .plugin(ContextPlugins.GROOVY_CONSOLE)
            .build();

    @BeforeEach
    void registerComponentManager() {
        when(componentManager.getComponents()).thenReturn(List.of());
        context.registerAdapter(ResourceResolver.class, ComponentManager.class, componentManager);
    }

    @Test
    void tablesNodesWithUnregisteredResourceTypes() {
        context.create().page("/content/we-retail", "template", "We Retail");
        context.create().resource("/content/we-retail/jcr:content/root",
                "sling:resourceType", "weretail/components/nonexistent");

        RunScriptResponse response = GroovyConsole.runScript(context, Samples.read("FindOrphanedComponents.groovy"));

        assertTrue(response.getExceptionStackTrace().isEmpty(), response.getExceptionStackTrace());
        assertTrue(response.getResult().contains("weretail/components/nonexistent"),
                "expected the orphaned resource type in the table but was: " + response.getResult());
    }
}
