package be.orbinson.aem.groovy.console.servlets.assist

import be.orbinson.aem.groovy.console.api.context.impl.RequestScriptContext
import be.orbinson.aem.groovy.console.configuration.ConfigurationService
import be.orbinson.aem.groovy.console.extension.ExtensionService
import be.orbinson.aem.groovy.console.servlets.AbstractJsonResponseServlet
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.SlingHttpServletResponse
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import javax.servlet.Servlet
import javax.servlet.ServletException

import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN

/**
 * Script context metadata (bindings, star imports, metaclasses) consumed by the modern UI's
 * reference panels and Monaco completion providers.
 */
@Component(service = Servlet, immediate = true, property = [
        "sling.servlet.paths=/bin/groovyconsole/assist/context"
])
class ContextServlet extends AbstractJsonResponseServlet {

    @Reference
    private ConfigurationService configurationService

    @Reference
    private ExtensionService extensionService

    @Override
    protected void doGet(SlingHttpServletRequest request,
                         SlingHttpServletResponse response) throws ServletException, IOException {
        if (!configurationService.hasPermission(request)) {
            response.status = SC_FORBIDDEN
            return
        }

        def scriptContext = new RequestScriptContext(
                request: request,
                response: response
        )

        def bindings = extensionService.getBindingVariables(scriptContext).collect { name, variable ->
            [
                    name: name,
                    type: variable.type?.name,
                    link: variable.link
            ]
        }

        def starImports = extensionService.starImports.collect { starImport ->
            [
                    packageName: starImport.packageName,
                    link       : starImport.link
            ]
        }

        def metaClasses = extensionService.metaClasses.collect { clazz ->
            [type: clazz.name]
        }

        writeJsonResponse(response, [
                bindings   : bindings,
                starImports: starImports,
                metaClasses: metaClasses
        ])
    }
}
