package be.orbinson.aem.groovy.console.servlets

import be.orbinson.aem.groovy.console.configuration.ConfigurationService
import be.orbinson.aem.groovy.console.response.impl.DefaultReplicateScriptResponse
import com.day.cq.replication.ReplicationActionType
import com.day.cq.replication.Replicator
import groovy.util.logging.Slf4j
import org.apache.commons.io.IOUtils
import org.apache.jackrabbit.JcrConstants
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.SlingHttpServletResponse
import org.apache.sling.api.resource.ResourceResolverFactory
import org.jetbrains.annotations.NotNull
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import javax.jcr.Session
import javax.servlet.Servlet
import javax.servlet.ServletException

import static be.orbinson.aem.groovy.console.constants.GroovyConsoleConstants.*
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN

@Component(service = Servlet, immediate = true, property = [
        "sling.servlet.paths=/bin/groovyconsole/replicate"
])
@Slf4j("LOG")
class ReplicateScriptServlet extends AbstractJsonResponseServlet {

    @Reference
    private ConfigurationService configurationService

    @Reference
    private ResourceResolverFactory resourceResolverFactory

    @Reference
    private Replicator replicator

    @Override
    protected void doPost(@NotNull SlingHttpServletRequest request, @NotNull SlingHttpServletResponse response) throws ServletException, IOException {
        if (configurationService.hasPermission(request) && configurationService.isDistributedExecutionEnabled()) {
            def script = request.getRequestParameter(SCRIPT)?.getString("UTF-8")
            if (script) {
                def resourceResolver = request.resourceResolver
                def scriptName = createScriptResource(script)
                LOG.debug("Replicate script '{}'", scriptName)
                def session = resourceResolver.adaptTo(Session)
                def scriptPath = "${PATH_REPLICATION_FOLDER}/${scriptName}"
                replicator.replicate(session, ReplicationActionType.ACTIVATE, "${scriptPath}")
                writeJsonResponse(response, new DefaultReplicateScriptResponse("Replicated script on path '${scriptPath}'"))
            } else {
                LOG.warn("Script should not be empty")
                response.status = SC_BAD_REQUEST
            }
        } else {
            response.status = SC_FORBIDDEN
        }
    }

    private String createScriptResource(String script) {
        resourceResolverFactory.getServiceResourceResolver(null).withCloseable { resourceResolver ->
            def parent = resourceResolver.getResource(PATH_REPLICATION_FOLDER)
            Map<String, Object> properties = new HashMap<>()

            properties.put(JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_FILE)
            def scriptName = "script-${System.currentTimeMillis()}.groovy"
            def scriptResource = resourceResolver.create(parent, scriptName, properties)

            properties.clear()
            properties.put(JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_RESOURCE)
            properties.put(JcrConstants.JCR_ENCODING, CHARSET)
            properties.put(JcrConstants.JCR_MIMETYPE, "application/octet-stream")
            properties.put(JcrConstants.JCR_DATA, IOUtils.toInputStream(script, CHARSET))
            resourceResolver.create(scriptResource, JcrConstants.JCR_CONTENT, properties)

            resourceResolver.commit()
            scriptName
        }
    }
}
