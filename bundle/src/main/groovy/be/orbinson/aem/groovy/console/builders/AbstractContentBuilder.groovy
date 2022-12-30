package be.orbinson.aem.groovy.console.builders

import javax.jcr.Node
import javax.jcr.Session

/**
 * Base class for <code>Page</code> and <code>Node</code> builders.
 */
abstract class AbstractContentBuilder extends BuilderSupport {

    Session session

    Node currentNode

    AbstractContentBuilder(Session session, Node currentNode) {
        this.session = session
        this.currentNode = currentNode
    }

    @Override
    void nodeCompleted(parent, node) {
        session.save()

        currentNode = currentNode.parent
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