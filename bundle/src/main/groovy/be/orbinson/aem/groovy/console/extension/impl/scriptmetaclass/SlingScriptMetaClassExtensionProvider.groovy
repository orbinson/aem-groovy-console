package be.orbinson.aem.groovy.console.extension.impl.scriptmetaclass

import be.orbinson.aem.groovy.console.api.ScriptMetaClassExtensionProvider
import be.orbinson.aem.groovy.console.api.context.ScriptContext
import be.orbinson.aem.groovy.console.table.Table
import org.apache.sling.models.factory.ModelFactory
import org.osgi.framework.BundleContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component

@Component(service = ScriptMetaClassExtensionProvider, immediate = true)
class SlingScriptMetaClassExtensionProvider implements ScriptMetaClassExtensionProvider {

    private BundleContext bundleContext

    @Override
    Closure getScriptMetaClass(ScriptContext scriptContext) {
        def resourceResolver = scriptContext.resourceResolver

        def closure = {

            delegate.getResource = { String path ->
                resourceResolver.getResource(path)
            }

            delegate.getModel = { String path, Class type ->
                def modelFactoryReference = bundleContext.getServiceReference(ModelFactory)
                def modelFactory = bundleContext.getService(modelFactoryReference)

                def resource = resourceResolver.resolve(path)

                modelFactory.createModel(resource, type)
            }

            delegate.table = { Closure closure ->
                def table = new Table()

                closure.delegate = table
                closure.resolveStrategy = DELEGATE_FIRST
                closure()

                table
            }
        }

        closure
    }

    @Activate
    void activate(BundleContext bundleContext) {
        this.bundleContext = bundleContext
    }
}
