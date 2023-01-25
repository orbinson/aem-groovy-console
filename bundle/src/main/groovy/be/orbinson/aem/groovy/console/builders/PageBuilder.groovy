package be.orbinson.aem.groovy.console.builders

import org.apache.jackrabbit.JcrConstants
import com.day.cq.wcm.api.NameConstants
import com.day.cq.wcm.api.Page

import javax.jcr.Node
import javax.jcr.Session

/**
 * Builder for AEM pages.  Each "node" in the syntax tree corresponds to a <code<cq:Page</code> node,
 * unless the node is a descendant of a <code>jcr:content</code> node, in which case nodes are treated in the same
 * manner as <code>NodeBuilder</code>.
 *
 * <pre>
 * pageBuilder.content {
 *     beer {
 *         styles("Styles") {
 *             "jcr:content"("jcr:lastModifiedBy": "me", "jcr:lastModified": Calendar.instance) {
 *                 data("sling:Folder")
 *             }
 *             dubbel("Dubbel")
 *             tripel("Tripel")
 *             saison("Saison")
 *         }
 *         breweries("Breweries", "jcr:lastModifiedBy": "me", "jcr:lastModified": Calendar.instance)
 *     }
 * }
 * </pre>
 *
 * <ul>
 *     <li>A single string argument is used to set the page title rather than the node type ("styles").</li>
 *     <li>Descendants of <code>jcr:content</code> nodes are not created with the <code>cq:Page</code> type by
 *     default and can have their own node type specified as described for the Node builder ("data").</li>
 *     <li>Page properties can be passed directly as arguments on the page node without explicitly creating a
 *     <code>jcr:content</code> node first ("breweries").</li>
 * </ul>
 */
class PageBuilder extends AbstractContentBuilder {

    private static final String NT_PAGE_CONTENT = "cq:PageContent"

    PageBuilder(Session session) {
        super(session, session.rootNode)
    }

    PageBuilder(Session session, Page rootPage) {
        super(session, session.getNode(rootPage.path))
    }

    PageBuilder(Session session, String rootPath) {
        super(session, session.getNode(rootPath))
    }

    @Override
    def createNode(name) {
        if (isContentNode(name)) {
            currentNode = currentNode.getOrAddNode(name)
        } else {
            currentNode = getOrAddPage(name: name)
        }

        currentNode
    }

    @Override
    def createNode(name, title) {
        if (isContentNode(name)) {
            currentNode = currentNode.getOrAddNode(name, title)
        } else {
            currentNode = getOrAddPage(name: name, title: title)
        }

        currentNode
    }

    @Override
    def createNode(name, Map properties) {
        if (isContentNode(name)) {
            currentNode = currentNode.getOrAddNode(name)

            setProperties(currentNode, properties)
        } else {
            currentNode = getOrAddPage(name: name, properties: properties)
        }

        currentNode
    }

    @Override
    def createNode(name, Map properties, value) {
        if (isContentNode(name)) {
            currentNode = currentNode.getOrAddNode(name, value)

            setProperties(currentNode, properties)
        } else {
            currentNode = getOrAddPage(name: name, title: value, properties: properties)
        }

        currentNode
    }

    private Node getOrAddPage(map) {
        def pageNode = currentNode.getOrAddNode(map.name, NameConstants.NT_PAGE)
        def contentNode = pageNode.getOrAddNode(JcrConstants.JCR_CONTENT, NT_PAGE_CONTENT)

        if (map.title) {
            contentNode.set(JcrConstants.JCR_TITLE, map.title)
        }

        if (map.properties) {
            setProperties(contentNode, map.properties)
        }

        pageNode
    }

    private boolean isContentNode(name) {
        name == JcrConstants.JCR_CONTENT || currentNode.path.contains(JcrConstants.JCR_CONTENT)
    }
}