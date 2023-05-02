package be.orbinson.aem.groovy.console.extension.impl.scriptmetaclass

import be.orbinson.aem.groovy.console.api.ScriptMetaClassExtensionProvider
import be.orbinson.aem.groovy.console.api.context.ScriptContext
import com.day.cq.replication.ReplicationActionType
import com.day.cq.replication.ReplicationOptions
import com.day.cq.replication.Replicator
import com.day.cq.search.PredicateGroup
import com.day.cq.search.QueryBuilder
import com.day.cq.wcm.api.PageManager
import org.apache.sling.distribution.DistributionRequest
import org.apache.sling.distribution.Distributor
import org.apache.sling.distribution.DistributionRequestType
import org.apache.sling.distribution.SimpleDistributionRequest
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import javax.jcr.Session

@Component(service = ScriptMetaClassExtensionProvider, immediate = true)
class AemScriptMetaClassExtensionProvider implements ScriptMetaClassExtensionProvider {

    @Reference
    private Replicator replicator

    @Reference
    private QueryBuilder queryBuilder

    @Reference
    private Distributor distributor;

    @Override
    Closure getScriptMetaClass(ScriptContext scriptContext) {
        def resourceResolver = scriptContext.resourceResolver
        def session = resourceResolver.adaptTo(Session)
        def pageManager = resourceResolver.adaptTo(PageManager)

        def closure = {

            delegate.getPage = { String path ->
                pageManager.getPage(path)
            }

            delegate.activate = { String path, ReplicationOptions options = null ->
                replicator.replicate(session, ReplicationActionType.ACTIVATE, path, options)
            }

            delegate.deactivate = { String path, ReplicationOptions options = null ->
                replicator.replicate(session, ReplicationActionType.DEACTIVATE, path, options)
            }

            delegate.delete { String path, ReplicationOptions options = null ->
                replicator.replicate(session, ReplicationActionType.DELETE, path, options)
            }

            delegate.distribute { String path, String agentId = "publish", boolean isDeep = false ->
                DistributionRequest distributionRequest = new SimpleDistributionRequest(DistributionRequestType.ADD, isDeep, path);
                distributor.distribute(agentId, resourceResolver, distributionRequest);
            }

            delegate.invalidate { String path, String agentId = "publish", boolean isDeep = false ->
                DistributionRequest distributionRequest = new SimpleDistributionRequest(DistributionRequestType.INVALIDATE, isDeep, path);
                distributor.distribute(agentId, resourceResolver, distributionRequest);
            }

            delegate.createQuery { Map predicates ->
                queryBuilder.createQuery(PredicateGroup.create(predicates), session)
            }

        }

        closure
    }

}
