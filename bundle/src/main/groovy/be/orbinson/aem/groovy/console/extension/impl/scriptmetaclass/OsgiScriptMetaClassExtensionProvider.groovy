package be.orbinson.aem.groovy.console.extension.impl.scriptmetaclass

import be.orbinson.aem.groovy.console.api.ScriptMetaClassExtensionProvider
import be.orbinson.aem.groovy.console.api.context.ScriptContext
import org.osgi.framework.BundleContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component

@Component(service = ScriptMetaClassExtensionProvider, immediate = true)
class OsgiScriptMetaClassExtensionProvider implements ScriptMetaClassExtensionProvider {

    private BundleContext bundleContext

    @Override
    Closure getScriptMetaClass(ScriptContext scriptContext) {

        def closure = {

            delegate.getService = { Class serviceType ->
                def serviceReference = bundleContext.getServiceReference(serviceType.name)

                bundleContext.getService(serviceReference)
            }

            delegate.getService = { String className ->
                def serviceReference = bundleContext.getServiceReference(className)

                bundleContext.getService(serviceReference)
            }

            delegate.getServices = { Class serviceType, String filter ->
                def serviceReferences = bundleContext.getServiceReferences(serviceType.name, filter)

                serviceReferences.collect { bundleContext.getService(it) }
            }

            delegate.getServices = { String className, String filter ->
                def serviceReferences = bundleContext.getServiceReferences(className, filter)

                serviceReferences.collect { bundleContext.getService(it) }
            }

        }

        closure
    }

    @Activate
    void activate(BundleContext bundleContext) {
        this.bundleContext = bundleContext
    }
}
