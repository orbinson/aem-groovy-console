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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the {@code CreateOsgiConfigurations.groovy} sample: creates a logger factory configuration per entry.
 */
@ExtendWith(AemContextExtension.class)
class CreateOsgiConfigurationsTest {

    private static final String FACTORY_PID = "org.apache.sling.commons.log.LogManager.factory.config";

    private final AemContext context = new AemContextBuilder()
            .plugin(ContextPlugins.GROOVY_CONSOLE)
            .build();

    @Test
    void createsAFactoryConfigurationPerLogger() throws Exception {
        ConfigurationAdmin configurationAdmin = context.getService(ConfigurationAdmin.class);

        RunScriptResponse response = GroovyConsole.runScript(context, Samples.read("CreateOsgiConfigurations.groovy"));

        assertTrue(response.getExceptionStackTrace().isEmpty(), response.getExceptionStackTrace());

        assertEquals("com.day.cq.dam",
                configurationAdmin.getFactoryConfiguration(FACTORY_PID, "dam")
                        .getProperties().get("org.apache.sling.commons.log.names"));
        assertEquals("be.orbinson.aem.groovy.console",
                configurationAdmin.getFactoryConfiguration(FACTORY_PID, "groovyconsole")
                        .getProperties().get("org.apache.sling.commons.log.names"));
    }
}
