package be.orbinson.aem.groovy.console.samples;

import be.orbinson.aem.groovy.console.response.RunScriptResponse;
import be.orbinson.aem.groovy.console.testing.ContextPlugins;
import be.orbinson.aem.groovy.console.testing.GroovyConsole;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextBuilder;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.service.cm.ConfigurationAdmin;

import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the {@code UpdateOsgiConfiguration.groovy} sample: appends a mapping to an existing OSGi configuration.
 */
@ExtendWith(AemContextExtension.class)
class UpdateOsgiConfigurationTest {

    private static final String PID = "org.apache.sling.jcr.resource.internal.JcrResourceResolverFactoryImpl";

    private final AemContext context = new AemContextBuilder()
            .plugin(ContextPlugins.GROOVY_CONSOLE)
            .build();

    @Test
    void appendsResourceResolverMapping() throws Exception {
        ConfigurationAdmin configurationAdmin = context.getService(ConfigurationAdmin.class);

        Dictionary<String, Object> seed = new Hashtable<>();
        seed.put("resource.resolver.mapping", new String[]{"/:/"});
        configurationAdmin.getConfiguration(PID).update(seed);

        RunScriptResponse response = GroovyConsole.runScript(context, Samples.read("UpdateOsgiConfiguration.groovy"));

        assertTrue(response.getExceptionStackTrace().isEmpty(), response.getExceptionStackTrace());

        String[] mappings = (String[]) configurationAdmin.getConfiguration(PID)
                .getProperties().get("resource.resolver.mapping");
        assertTrue(Arrays.asList(mappings).contains("/content/we-retail/:/wr/"),
                "expected the new mapping but was: " + Arrays.toString(mappings));
    }
}
