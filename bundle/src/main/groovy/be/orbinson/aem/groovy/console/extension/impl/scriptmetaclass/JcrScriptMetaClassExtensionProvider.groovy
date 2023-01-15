package be.orbinson.aem.groovy.console.extension.impl.scriptmetaclass

import be.orbinson.aem.groovy.console.api.ScriptMetaClassExtensionProvider
import be.orbinson.aem.groovy.console.api.context.ScriptContext
import org.osgi.service.component.annotations.Component

import javax.jcr.Node
import javax.jcr.Session

@Component(service = ScriptMetaClassExtensionProvider, immediate = true)
class JcrScriptMetaClassExtensionProvider implements ScriptMetaClassExtensionProvider {

    @Override
    Closure getScriptMetaClass(ScriptContext scriptContext) {
        def resourceResolver = scriptContext.resourceResolver
        def session = resourceResolver.adaptTo(Session)

        def closure = {
            delegate.getNode = { String path ->
                session.getNode(path)
            }

            delegate.move = { String src ->
                ["to": { String dst ->
                    session.move(src, dst)
                    session.save()
                }]
            }

            delegate.rename = { Node node ->
                ["to": { String newName ->
                    def parent = node.parent

                    delegate.move node.path to parent.path + "/" + newName

                    if (parent.primaryNodeType.hasOrderableChildNodes()) {
                        def nextSibling = node.nextSibling as Node

                        if (nextSibling) {
                            parent.orderBefore(newName, nextSibling.name)
                        }
                    }

                    session.save()
                }]
            }

            delegate.copy = { String src ->
                ["to": { String dst ->
                    session.workspace.copy(src, dst)
                }]
            }

            delegate.save = {
                session.save()
            }

            delegate.xpathQuery { String query ->
                session.workspace.queryManager.createQuery(query, "xpath").execute().nodes
            }

            delegate.sql2Query { String query ->
                session.workspace.queryManager.createQuery(query, "JCR-SQL2").execute().nodes
            }

        }

        closure
    }

}
