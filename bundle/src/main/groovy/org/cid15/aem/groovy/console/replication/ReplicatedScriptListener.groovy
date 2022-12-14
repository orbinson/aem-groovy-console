package org.cid15.aem.groovy.console.replication

import com.day.cq.commons.jcr.JcrConstants
import com.google.common.base.Charsets
import groovy.util.logging.Slf4j
import org.apache.sling.api.resource.ResourceResolver
import org.apache.sling.api.resource.ResourceResolverFactory
import org.apache.sling.api.resource.observation.ResourceChange
import org.apache.sling.api.resource.observation.ResourceChangeListener
import org.cid15.aem.groovy.console.GroovyConsoleService
import org.cid15.aem.groovy.console.api.context.ScriptContext
import org.cid15.aem.groovy.console.api.impl.ResourceScriptContext
import org.jetbrains.annotations.NotNull
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import javax.jcr.Session

import static com.google.common.base.Preconditions.checkNotNull

// TODO: Use const references for properties
@Component(property = [
        "resource.paths=/conf/groovyconsole/distribution",
        "resource.change.types=ADDED"
])
@Slf4j("LOG")
public class ReplicatedScriptListener implements ResourceChangeListener {
    @Reference
    private GroovyConsoleService consoleService;

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Override
    public void onChange(@NotNull List<ResourceChange> list) {
        // TODO: Only run on publish
        list.each { change ->
            resourceResolverFactory.getServiceResourceResolver(null).withCloseable { resourceResolver ->
                LOG.info("Detected replicated script on path '{}'", change.path)
                consoleService.runScript(getScriptContext(resourceResolver, change.path));
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
