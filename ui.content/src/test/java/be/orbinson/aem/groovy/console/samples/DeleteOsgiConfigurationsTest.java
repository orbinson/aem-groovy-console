package be.orbinson.aem.groovy.console.samples;

import be.orbinson.aem.groovy.console.response.RunScriptResponse;
import be.orbinson.aem.groovy.console.testing.ContextPlugins;
import be.orbinson.aem.groovy.console.testing.GroovyConsole;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextBuilder;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

import java.util.Dictionary;
import java.util.Hashtable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the {@code DeleteOsgiConfigurations.groovy} sample
 */
@ExtendWith(AemContextExtension.class)
class DeleteOsgiConfigurationsTest {

    private static final String FACTORY_PID = "com.day.cq.compat.migration.factory.location";

    private static final String FILTER = "(service.factoryPid=" + FACTORY_PID + ")";

    private final AemContext context = new AemContextBuilder()
            .plugin(ContextPlugins.GROOVY_CONSOLE)
            .build();

    @Test
    void deletesConfigurationsMatchingTheFactoryPid() throws Exception {
        ConfigurationAdmin configurationAdmin = context.getService(ConfigurationAdmin.class);

        Configuration configuration = configurationAdmin.getFactoryConfiguration(FACTORY_PID, "location-1");
        Dictionary<String, Object> properties = new Hashtable<>();
        properties.put("location", "/content");
        configuration.update(properties);

        assertEquals(1, configurationAdmin.listConfigurations(FILTER).length);

        RunScriptResponse response = GroovyConsole.runScript(context, Samples.read("DeleteOsgiConfigurations.groovy"));

        assertTrue(response.getExceptionStackTrace().isEmpty(), response.getExceptionStackTrace());
        assertNull(configurationAdmin.listConfigurations(FILTER));
    }
}
