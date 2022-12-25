package be.orbinson.aem.groovy.console.builders

import javax.jcr.Node
import javax.jcr.Session

/**
 * Builder for JCR content nodes.  Each "node" in the syntax tree corresponds to a JCR node in the repository. A new
 * JCR node is created only if there is no existing node for the current name.
 *
 * <pre>
 * nodeBuilder.etc {
 *     satirists("sling:Folder") {
 *         bierce(firstName: "Ambrose", lastName: "Bierce", birthDate: Calendar.instance.updated(year: 1842, month: 5, date: 24))
 *         mencken(firstName: "H.L.", lastName: "Mencken", birthDate: Calendar.instance.updated(year: 1880, month: 8, date: 12))
 *         other("sling:Folder", "jcr:title": "Other")
 *     }
 * }
 * </pre>
 *
 * <ul>
 *     <li>A single string argument represents the node type name for the node ("satirists").</li>
 *     <li>A map argument uses the provided key:value pairs to set property values on the node ("bierce" and
 *     "mencken").</li>
 *     <li>Both string and map arguments will set the node type and property value(s) for the node ("other").</li>
 * </ul>
 */
class NodeBuilder extends AbstractContentBuilder {

    NodeBuilder(Session session) {
        super(session, session.rootNode)
    }

    NodeBuilder(Session session, Node rootNode) {
        super(session, rootNode)
    }

    NodeBuilder(Session session, String rootPath) {
        super(session, session.getNode(rootPath))
    }

    @Override
    def createNode(name) {
        currentNode = currentNode.getOrAddNode(name)

        currentNode
    }

    @Override
    def createNode(name, primaryNodeTypeName) {
        currentNode = currentNode.getOrAddNode(name, primaryNodeTypeName)

        currentNode
    }

    @Override
    def createNode(name, Map properties) {
        currentNode = currentNode.getOrAddNode(name)

        setProperties(currentNode, properties)

        currentNode
    }

    @Override
    def createNode(name, Map properties, primaryNodeTypeName) {
        currentNode = createNode(name, primaryNodeTypeName)

        setProperties(currentNode, properties)

        currentNode
    }
}