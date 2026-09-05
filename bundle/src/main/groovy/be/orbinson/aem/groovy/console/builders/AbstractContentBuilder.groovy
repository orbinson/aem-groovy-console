package be.orbinson.aem.groovy.console.builders

import javax.jcr.Node
import javax.jcr.Session

/**
 * Base class for <code>Page</code> and <code>Node</code> builders.
 */
abstract class AbstractContentBuilder extends BuilderSupport {

    Session session

    // Backing field for the currentNode property. Named distinctly so that subclass access to "currentNode" always
    // routes through getCurrentNode()/setCurrentNode() rather than the field, keeping root resolution lazy.
    private Node resolvedNode

    // Resolves the root node on first use, so constructing a builder does not require a live JCR session.
    private Closure<Node> rootNodeResolver

    AbstractContentBuilder(Session session, Node currentNode) {
        this.session = session
        this.resolvedNode = currentNode
    }

    AbstractContentBuilder(Session session, Closure<Node> rootNodeResolver) {
        this.session = session
        this.rootNodeResolver = rootNodeResolver
    }

    Node getCurrentNode() {
        if (resolvedNode == null && rootNodeResolver != null) {
            resolvedNode = rootNodeResolver.call()
            rootNodeResolver = null
        }

        resolvedNode
    }

    void setCurrentNode(Node currentNode) {
        this.resolvedNode = currentNode
    }

    @Override
    void nodeCompleted(parent, node) {
        session.save()

        resolvedNode = getCurrentNode().parent
    }

    @Override
    void setParent(parent, child) {

    }

    void setProperties(node, properties) {
        properties.each { name, value ->
            node.set(name, value)
        }
    }
}
