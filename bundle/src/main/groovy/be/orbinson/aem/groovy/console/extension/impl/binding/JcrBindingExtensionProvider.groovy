package be.orbinson.aem.groovy.console.extension.impl.binding

import be.orbinson.aem.groovy.console.api.BindingExtensionProvider
import be.orbinson.aem.groovy.console.api.BindingVariable
import be.orbinson.aem.groovy.console.api.context.ScriptContext
import be.orbinson.aem.groovy.console.builders.NodeBuilder
import org.osgi.service.component.annotations.Component

import javax.jcr.Session

@Component(service = BindingExtensionProvider, immediate = true)
class JcrBindingExtensionProvider implements BindingExtensionProvider {

    @Override
    Map<String, BindingVariable> getBindingVariables(ScriptContext scriptContext) {
        def resourceResolver = scriptContext.resourceResolver
        def session = resourceResolver.adaptTo(Session)

        def bindingVariables = [
                session    : new BindingVariable(session, Session,
                        "https://developer.adobe.com/experience-manager/reference-materials/spec/javax.jcr/javadocs/jcr-2.0/javax/jcr/Session.html"),
                nodeBuilder: new BindingVariable(new NodeBuilder(session), NodeBuilder,
                        "                        https://orbinson.github.io/aem-groovy-console/be/orbinson/aem/groovy/console/builders/NodeBuilder.html")
        ]

        bindingVariables
    }

}
