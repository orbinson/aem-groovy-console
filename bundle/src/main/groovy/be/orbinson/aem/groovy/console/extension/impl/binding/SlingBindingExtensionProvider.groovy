package be.orbinson.aem.groovy.console.extension.impl.binding

import be.orbinson.aem.groovy.console.api.BindingExtensionProvider
import be.orbinson.aem.groovy.console.api.BindingVariable
import be.orbinson.aem.groovy.console.api.context.ScriptContext
import be.orbinson.aem.groovy.console.api.context.ServletScriptContext
import groovy.json.JsonException
import groovy.json.JsonSlurper
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.SlingHttpServletResponse
import org.apache.sling.api.resource.ResourceResolver
import org.osgi.framework.BundleContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Component(service = BindingExtensionProvider, immediate = true)
class SlingBindingExtensionProvider implements BindingExtensionProvider {

    private BundleContext bundleContext

    @Override
    Map<String, BindingVariable> getBindingVariables(ScriptContext scriptContext) {
        def resourceResolver = scriptContext.resourceResolver

        def bindingVariables = [
                log             : new BindingVariable(LoggerFactory.getLogger("groovyconsole"), Logger,
                        "https://www.slf4j.org/api/org/slf4j/Logger.html"),
                resourceResolver: new BindingVariable(resourceResolver, ResourceResolver,
                        "https://sling.apache.org/apidocs/sling12/org/apache/sling/api/resource/ResourceResolver.html"),
                bundleContext   : new BindingVariable(bundleContext, BundleContext,
                        "https://docs.osgi.org/javadoc/r4v43/core/org/osgi/framework/BundleContext.html"),
                out             : new BindingVariable(scriptContext.printStream, PrintStream,
                        "https://docs.oracle.com/javase/8/docs/api/java/io/PrintStream.html")
        ]

        if (scriptContext instanceof ServletScriptContext) {
            bindingVariables.putAll([
                    slingRequest : new BindingVariable(scriptContext.request, SlingHttpServletRequest,
                            "https://sling.apache.org/apidocs/sling12/org/apache/sling/api/SlingHttpServletRequest.html"),
                    slingResponse: new BindingVariable(scriptContext.response, SlingHttpServletResponse,
                            "https://sling.apache.org/apidocs/sling12/org/apache/sling/api/SlingHttpServletResponse.html")
            ])
        }

        if (scriptContext.data) {
            try {
                def json = new JsonSlurper().parseText(scriptContext.data)

                bindingVariables["data"] = new BindingVariable(json, json.class)
            } catch (JsonException ignored) {
                // if data cannot be parsed as a JSON object, bind it as a String
                bindingVariables["data"] = new BindingVariable(scriptContext.data, String)
            }
        }

        bindingVariables
    }

    @Activate
    void activate(BundleContext bundleContext) {
        this.bundleContext = bundleContext
    }
}
