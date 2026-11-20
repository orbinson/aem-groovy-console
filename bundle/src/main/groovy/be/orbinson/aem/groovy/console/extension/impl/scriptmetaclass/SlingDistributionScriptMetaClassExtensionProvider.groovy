package be.orbinson.aem.groovy.console.extension.impl.scriptmetaclass

import be.orbinson.aem.groovy.console.api.ScriptMetaClassExtensionProvider
import be.orbinson.aem.groovy.console.api.context.ScriptContext
import org.apache.sling.distribution.Distributor
import org.apache.sling.distribution.DistributionRequestType
import org.apache.sling.distribution.SimpleDistributionRequest
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = ScriptMetaClassExtensionProvider, immediate = true)
class SlingDistributionScriptMetaClassExtensionProvider implements ScriptMetaClassExtensionProvider {

    @Reference
    private Distributor distributor

    @Override
    Closure getScriptMetaClass(ScriptContext scriptContext) {
        def resourceResolver = scriptContext.resourceResolver

        def closure = {
            delegate.distribute = { String path, String agentId = "publish", boolean isDeep = false ->
                def request = new SimpleDistributionRequest(DistributionRequestType.ADD, isDeep, path)
                distributor.distribute(agentId, resourceResolver, request)
            }

            delegate.invalidate = { String path, String agentId = "publish", boolean isDeep = false ->
                def request = new SimpleDistributionRequest(DistributionRequestType.INVALIDATE, isDeep, path)
                distributor.distribute(agentId, resourceResolver, request)
            }
        }

        closure
    }
}
