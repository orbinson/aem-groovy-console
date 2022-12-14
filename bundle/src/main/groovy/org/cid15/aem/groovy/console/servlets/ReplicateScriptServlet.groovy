package org.cid15.aem.groovy.console.servlets

import com.day.cq.commons.jcr.JcrConstants
import com.google.common.base.Charsets
import com.google.common.net.MediaType
import groovy.util.logging.Slf4j
import org.apache.commons.io.IOUtils
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.SlingHttpServletResponse
import org.apache.sling.api.resource.ResourceResolverFactory
import org.cid15.aem.groovy.console.configuration.ConfigurationService
import org.jetbrains.annotations.NotNull
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import javax.servlet.Servlet
import javax.servlet.ServletException

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN
import static org.cid15.aem.groovy.console.constants.GroovyConsoleConstants.CHARSET
import static org.cid15.aem.groovy.console.constants.GroovyConsoleConstants.PATH_REPLICATION_FOLDER
import static org.cid15.aem.groovy.console.constants.GroovyConsoleConstants.SCRIPT

@Component(service = Servlet, immediate = true, property = [
        "sling.servlet.paths=/bin/groovyconsole/replicate"
])
@Slf4j("LOG")
class ReplicateScriptServlet extends AbstractJsonResponseServlet {
    @Reference
    private ConfigurationService configurationService

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Override
    protected void doPost(@NotNull SlingHttpServletRequest request, @NotNull SlingHttpServletResponse response) throws ServletException, IOException {
        if (configurationService.hasPermission(request)) {
            def script = request.getRequestParameter(SCRIPT)?.getString(Charsets.UTF_8.name())
            if (script) {
                LOG.info("Replicate script")
                createScriptResource(script)
                // TODO: replicate script, cleanup and provide response code
            } else {
                LOG.warn("Script should not be empty")
                response.status = SC_BAD_REQUEST
            }
        } else {
            response.status = SC_FORBIDDEN
        }
    }

    void createScriptResource(String script) {
        resourceResolverFactory.getServiceResourceResolver(null).withCloseable { resourceResolver ->
            def parent = resourceResolver.getResource(PATH_REPLICATION_FOLDER)
            Map<String, Object> properties = new HashMap<>()

            properties.put(JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_FILE)
            // TODO: generate file name script-<unix-timestamp>.groovy
            def scriptResource = resourceResolver.create(parent, "script.groovy", properties)

            properties.clear()
            properties.put(JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_RESOURCE)
            properties.put(JcrConstants.JCR_ENCODING, CHARSET)
            properties.put(JcrConstants.JCR_MIMETYPE, MediaType.OCTET_STREAM.toString())
            properties.put(JcrConstants.JCR_DATA, IOUtils.toInputStream(script, CHARSET))
            resourceResolver.create(scriptResource, JcrConstants.JCR_CONTENT, properties)

            resourceResolver.commit()
        }
    }
}
