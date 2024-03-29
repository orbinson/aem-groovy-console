package be.orbinson.aem.groovy.console.servlets

import be.orbinson.aem.groovy.console.GroovyConsoleService
import be.orbinson.aem.groovy.console.api.context.ScriptContext
import be.orbinson.aem.groovy.console.api.context.impl.RequestScriptContext
import be.orbinson.aem.groovy.console.configuration.ConfigurationService
import org.apache.jackrabbit.JcrConstants
import com.google.common.base.Charsets
import groovy.util.logging.Slf4j
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.SlingHttpServletResponse
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import javax.jcr.Session
import javax.servlet.Servlet
import javax.servlet.ServletException

import static be.orbinson.aem.groovy.console.constants.GroovyConsoleConstants.SCRIPT
import static be.orbinson.aem.groovy.console.constants.GroovyConsoleConstants.SCRIPT_PATH
import static be.orbinson.aem.groovy.console.constants.GroovyConsoleConstants.SCRIPT_PATHS

import static com.google.common.base.Preconditions.checkNotNull
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN

@Component(service = Servlet, immediate = true, property = [
        "sling.servlet.paths=/bin/groovyconsole/post"
])
@Slf4j("LOG")
class ScriptPostServlet extends AbstractJsonResponseServlet {

    @Reference
    private ConfigurationService configurationService

    @Reference
    private GroovyConsoleService consoleService

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws
            ServletException, IOException {
        if (configurationService.hasPermission(request)) {
            def scriptPaths = request.getParameterValues(SCRIPT_PATHS)

            if (scriptPaths) {
                LOG.debug("running scripts for paths : {}", scriptPaths)

                def runScriptResponses = scriptPaths.collect { scriptPath ->
                    def scriptContext = getScriptContext(request, response, scriptPath)

                    consoleService.runScript(scriptContext)
                }

                writeJsonResponse(response, runScriptResponses)
            } else {
                def scriptPath = request.getParameter(SCRIPT_PATH)

                if (scriptPath) {
                    LOG.debug("running script for path : {}", scriptPath)
                }

                def scriptContext = getScriptContext(request, response, scriptPath)

                writeJsonResponse(response, consoleService.runScript(scriptContext))
            }
        } else {
            response.status = SC_FORBIDDEN
        }
    }

    private ScriptContext getScriptContext(SlingHttpServletRequest request, SlingHttpServletResponse response, String scriptPath) {
        def outputStream = new ByteArrayOutputStream()

        new RequestScriptContext(
                request: request,
                response: response,
                outputStream: outputStream,
                printStream: new PrintStream(outputStream, true, Charsets.UTF_8.name()),
                script: checkNotNull(getScript(request, scriptPath), "Script cannot be empty.")
        )
    }

    private String getScript(SlingHttpServletRequest request, String scriptPath) {
        if (scriptPath) {
            loadScript(request, scriptPath)
        } else {
            request.getRequestParameter(SCRIPT)?.getString(Charsets.UTF_8.name())
        }
    }

    private String loadScript(SlingHttpServletRequest request, String scriptPath) {
        def session = request.resourceResolver.adaptTo(Session)

        def binary = session.getNode(scriptPath)
                .getNode(JcrConstants.JCR_CONTENT)
                .getProperty(JcrConstants.JCR_DATA)
                .binary

        def script = binary.stream.text

        binary.dispose()

        script
    }
}
