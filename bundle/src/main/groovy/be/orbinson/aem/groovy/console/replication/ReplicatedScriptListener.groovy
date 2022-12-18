package be.orbinson.aem.groovy.console.replication

import be.orbinson.aem.groovy.console.api.context.ScriptContext
import be.orbinson.aem.groovy.console.api.impl.ResourceScriptContext
import be.orbinson.aem.groovy.console.configuration.ConfigurationService
import com.day.cq.commons.jcr.JcrConstants
import com.google.common.base.Charsets
import groovy.util.logging.Slf4j
import org.apache.sling.api.resource.ResourceResolver
import org.apache.sling.api.resource.ResourceResolverFactory
import org.apache.sling.api.resource.observation.ResourceChange
import org.apache.sling.api.resource.observation.ResourceChangeListener
import be.orbinson.aem.groovy.console.GroovyConsoleService
import org.jetbrains.annotations.NotNull
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import javax.jcr.Session

import static com.google.common.base.Preconditions.checkNotNull

@Component(property = [
        "resource.paths=glob:/conf/groovyconsole/replication/*.groovy",
        "resource.change.types=ADDED"
])
@Slf4j("LOG")
public class ReplicatedScriptListener implements ResourceChangeListener {
    @Reference
    private GroovyConsoleService consoleService;

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Reference
    private ConfigurationService configurationService;

    @Override
    public void onChange(@NotNull List<ResourceChange> list) {
        if (!configurationService.isAuthor() && configurationService.isDistributedExecutionEnabled()) {
            list.each { change ->
                resourceResolverFactory.getServiceResourceResolver(null).withCloseable { resourceResolver ->
                    LOG.debug("Detected replicated script on path '{}'", change.path)
                    consoleService.runScript(getScriptContext(resourceResolver, change.path))
                }
            }
        }
    }

    private ScriptContext getScriptContext(ResourceResolver resourceResolver, String scriptPath) {
        def outputStream = new ByteArrayOutputStream()

        new ResourceScriptContext(
                resourceResolver: resourceResolver,
                outputStream: outputStream,
                printStream: new PrintStream(outputStream, true, Charsets.UTF_8.name()),
                script: checkNotNull(loadScript(resourceResolver, scriptPath), "Script cannot be empty.")
        )
    }

    // FIXME: extract to service that can be shared between services and servlets
    private String loadScript(ResourceResolver resourceResolver, String scriptPath) {
        def session = resourceResolver.adaptTo(Session)

        // FIXME: use adaptTo(InputStream.class) to get binary data
        def binary = session.getNode(scriptPath)
                .getNode(JcrConstants.JCR_CONTENT)
                .getProperty(JcrConstants.JCR_DATA)
                .binary

        def script = binary.stream.text

        binary.dispose()

        script
    }
}
