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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the {@code CreatePackage.groovy} sample: builds a FileVault package definition node under /etc/packages.
 */
@ExtendWith(AemContextExtension.class)
class CreatePackageTest {

    private final AemContext context = new AemContextBuilder(ResourceResolverType.JCR_MOCK)
            .plugin(ContextPlugins.GROOVY_CONSOLE)
            .build();

    @Test
    void createsPackageDefinition() {
        context.create().resource("/etc/packages", "jcr:primaryType", "sling:Folder");

        RunScriptResponse response = GroovyConsole.runScript(context, Samples.read("CreatePackage.groovy"));

        assertTrue(response.getExceptionStackTrace().isEmpty(), response.getExceptionStackTrace());
        assertNotNull(context.resourceResolver()
                .getResource("/etc/packages/groovy-console-history.zip/jcr:content/vlt:definition/filter/filter0"), "expected the filter node to be created");
    }
}
