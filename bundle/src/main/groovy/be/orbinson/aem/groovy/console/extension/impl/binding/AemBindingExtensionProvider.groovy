package be.orbinson.aem.groovy.console.extension.impl.binding

import be.orbinson.aem.groovy.console.api.BindingExtensionProvider
import be.orbinson.aem.groovy.console.api.BindingVariable
import be.orbinson.aem.groovy.console.api.context.ScriptContext
import be.orbinson.aem.groovy.console.builders.PageBuilder
import com.day.cq.wcm.api.PageManager
import com.day.cq.wcm.api.PageManagerFactory
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import javax.jcr.Session

@Component(service = BindingExtensionProvider, immediate = true)
class AemBindingExtensionProvider implements BindingExtensionProvider {

    // Reference to make sure that this class does not become active when running in Sling instead of AEM
    @Reference
    private PageManagerFactory pageManagerFactory

    @Override
    Map<String, BindingVariable> getBindingVariables(ScriptContext scriptContext) {
        def resourceResolver = scriptContext.resourceResolver
        def session = resourceResolver.adaptTo(Session)

        def bindingVariables = [
                pageManager: new BindingVariable(resourceResolver.adaptTo(PageManager), PageManager),
                pageBuilder: new BindingVariable(new PageBuilder(session), PageBuilder,
                        "https://orbinson.github.io/aem-groovy-console/be/orbinson/aem/groovy/console/builders/PageBuilder.html"),
        ]

        bindingVariables
    }

}
