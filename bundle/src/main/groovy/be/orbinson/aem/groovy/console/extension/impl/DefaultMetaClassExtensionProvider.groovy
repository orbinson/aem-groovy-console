package be.orbinson.aem.groovy.console.extension.impl

import be.orbinson.aem.groovy.console.extension.MetaClassExtensionProvider;
import com.day.cq.wcm.api.Page
import com.google.common.base.Optional
import groovy.util.logging.Slf4j
import org.osgi.service.component.annotations.Component

import javax.jcr.Binary
import javax.jcr.Node
import javax.jcr.PropertyType
import javax.jcr.Session
import javax.jcr.Value
import javax.servlet.ServletRequest

/**
 * This default metaclass provider adds additional methods to all instances of the classes outlined below.
 * <p/>
 * <a href="https://docs.adobe.com/docs/en/aem/6-2/develop/ref/javadoc/com/day/cq/wcm/api/Page.html">com.day.cq.wcm.api.Page</a>
 *
 * <ul>
 *     <li>iterator() - Allows usage of Groovy closure operators (<code>each</code>,
 *     <code>eachWithIndex</code>) to iterate over child pages of the current page.</li>
 *     <li>recurse(Closure closure) - Recursively invoke this closure on each descendant page of the current page.</li>
 *     <li>getNode() - Get the <code>jcr:content</code> node of the current page, returning null if it does not exist
 *     .</li>
 *     <li>get(String propertyName) - Get the named property value from the <code>jcr:content</code> node of the
 *     current page, with the return type determined dynamically by <a href="https://developer.adobe.com/experience-manager/reference-materials/spec/jsr170/javadocs/jcr-2.0/javax/jcr/Property.html#getType()" target="_blank">Property.getType()</a>
 *     .</li>
 *     <li>set(String propertyName, Object value) - Set the named property value on the <code>jcr:content</code> node
 *     of the current page.  An array value argument can be used to set multi-valued properties.</li>
 * </ul>
 *
 * <a href="https://docs.adobe.com/content/docs/en/spec/jsr170/javadocs/jcr-2.0/javax/jcr/Node.html">javax.jcr.Node</a>
 *
 * <ul>
 *     <li>iterator() - Allows usage of Groovy closure operators (<code>each</code>,
 *     <code>eachWithIndex</code>) to iterate over child nodes of the current node.</li>
 *     <li>get(String propertyName) - Get the named property value, with the return type determined dynamically by <a
 *     href="https://docs.adobe.com/docs/en/spec/jsr170/javadocs/jcr-2.0/javax/jcr/Property.html#getType()"
 *     target="_blank">Property.getType()</a>.</li>
 *     <li>set(String propertyName, Object value) - Set the named property value.  An array value argument can be
 *     used to set multi-valued properties.</li>
 *     <li>set(Map properties) - Set properties using the key/value pairs in the map as the property names/values.</li>
 *     <li>getOrAddNode(String name) - Get the named child node if it exists; otherwise, add it.</li>
 *     <li>getOrAddNode(String name, String primaryNodeTypeName) - Get the named child node if it exists; otherwise,
 *     add it with the given node type.</li>
 *     <li>removeNode(String name) - Remove the child node with the given name,
 *     returning true if the node was removed.</li>
 *     <li>recurse(Closure closure) - Recursively invoke this closure on each descendant node of the current node.</li>
 *     <li>recurse(String primaryNodeTypeName, Closure closure) - Recursively invoke this closure on each descendant
 *     node of the current node that matches the given node type.</li>
 *     <li>recurse(Collection&lt;String&gt; primaryNodeTypeNames, Closure closure) - Recursively invoke this closure
 *     on each descendant node of the current node that matches any of the given node types.</li>
 * </ul>
 *
 * <a href="https://docs.adobe.com/content/docs/en/spec/jsr170/javadocs/jcr-2.0/javax/jcr/Binary.html">javax.jcr.Binary</a>
 *
 * <ul>
 *     <li>withBinary(Closure closure) - Execute the closure and automatically dispose of the binary's resources when
 *     the closure completes.  The closure accepts a single argument with the current binary instance.</li>
 * </ul>
 *
 * <a href="https://docs.oracle.com/javaee/6/api/javax/servlet/ServletRequest.html">javax.servlet.ServletRequest</a>
 *
 * <ul>
 *     <li>getAt(String name) - Supports use of the <a href="https://groovy-lang.org/operators.html#_other_operators">
 *     subscript operator</a> to get a request parameter value.  If the
 *     value is an array, a list will be returned.</li>
 * </ul>
 */
@Component(service = MetaClassExtensionProvider, immediate = true)
@Slf4j("LOG")
class DefaultMetaClassExtensionProvider implements MetaClassExtensionProvider {

    static def OPTIONAL_METACLASS = {
        asBoolean {
            def optional = delegate as Optional

            optional != null && optional.present
        }
    }

    static def SERVLET_REQUEST_METACLASS = {
        getAt { String parameterName ->
            def request = delegate as ServletRequest
            def value = request.parameterMap[parameterName] as String[]
            def result

            if (value) {
                result = value.length > 1 ? value as List : value[0]
            } else {
                result = null
            }

            result
        }
    }

    static def BINARY_METACLASS = {
        withBinary { Closure c ->
            def binary = delegate as Binary
            def result = null

            try {
                result = c(binary)
            } finally {
                binary.dispose()
            }

            result
        }
    }

    static def NODE_METACLASS = {
        iterator {
            (delegate as Node).nodes
        }

        recurse { Closure closure ->
            def node = delegate as Node

            closure(delegate)

            node.nodes.each { child ->
                child.recurse(closure)
            }
        }

        recurse { String primaryNodeTypeName, Closure closure ->
            def node = delegate as Node

            if (node.primaryNodeType.name == primaryNodeTypeName) {
                closure(delegate)
            }

            node.nodes.findAll { it.primaryNodeType.name == primaryNodeTypeName }.each { child ->
                child.recurse(primaryNodeTypeName, closure)
            }
        }

        recurse { Collection<String> primaryNodeTypeNames, Closure closure ->
            def node = delegate as Node

            if (primaryNodeTypeNames.contains(node.primaryNodeType.name)) {
                closure(node)
            }

            node.nodes.findAll { primaryNodeTypeNames.contains(it.primaryNodeType.name) }.each { child ->
                child.recurse(primaryNodeTypeNames, closure)
            }
        }

        get { String propertyName ->
            def result = null

            def node = delegate as Node

            if (node.hasProperty(propertyName)) {
                def property = node.getProperty(propertyName)

                if (property.multiple) {
                    result = property.values.collect { getResult(node.session, it) }
                } else {
                    result = getResult(node.session, property.value)
                }
            }

            result
        }

        set { String propertyName, value ->
            def node = delegate as Node

            if (value == null) {
                if (node.hasProperty(propertyName)) {
                    node.getProperty(propertyName).remove()
                }
            } else {
                def valueFactory = node.session.valueFactory

                if (value instanceof Object[] || value instanceof Collection) {
                    def values = value.collect { valueFactory.createValue(it) }.toArray(new Value[0])

                    node.setProperty(propertyName, values)
                } else {
                    def jcrValue = valueFactory.createValue(value)

                    node.setProperty(propertyName, jcrValue)
                }
            }
        }

        set { Map properties ->
            properties?.each { name, value ->
                delegate.set(name, value)
            }
        }

        getOrAddNode { String name ->
            def node = delegate as Node

            node.hasNode(name) ? node.getNode(name) : node.addNode(name)
        }

        getOrAddNode { String name, String primaryNodeTypeName ->
            def node = delegate as Node

            node.hasNode(name) ? node.getNode(name) : node.addNode(name, primaryNodeTypeName)
        }

        removeNode { String name ->
            def node = delegate as Node

            boolean removed = false

            if (node.hasNode(name)) {
                node.getNode(name).remove()
                removed = true
            }

            removed
        }

        getNextSibling {
            def node = delegate as Node

            def siblings = node.parent.nodes

            while (siblings.hasNext()) {
                if (node.isSame(siblings.nextNode())) {
                    break
                }
            }

            siblings.hasNext() ? siblings.nextNode() : null
        }

        getPrevSibling {
            def node = delegate as Node

            def siblings = node.parent.nodes
            def prevSibling = null
            def result = null

            while (siblings.hasNext()) {
                def sibling = siblings.nextNode()

                if (node.isSame(sibling)) {
                    result = prevSibling
                    break
                }

                prevSibling = sibling
            }

            result
        }
    }

    static def PAGE_METACLASS = {
        iterator {
            (delegate as Page).listChildren()
        }

        recurse { Closure closure ->
            closure(delegate)

            (delegate as Page).listChildren().each { child ->
                child.recurse(closure)
            }
        }

        getNode {
            (delegate as Page).contentResource?.adaptTo(Node)
        }

        get { String name ->
            (delegate as Page).contentResource?.adaptTo(Node)?.get(name)
        }

        set { String name, value ->
            def node = (delegate as Page).contentResource?.adaptTo(Node)

            if (node) {
                node.set(name, value)
            }
        }
    }

    static def DEFAULT_METACLASSES = [
            (Optional): OPTIONAL_METACLASS,
            (ServletRequest): SERVLET_REQUEST_METACLASS,
            (Binary): BINARY_METACLASS,
            (Node): NODE_METACLASS,
            (Page): PAGE_METACLASS
    ]

    @Override
    Map<Class, Closure> getMetaClasses() {
        DEFAULT_METACLASSES
    }

    private static def getResult(Session session, Value value) {
        def result = null

        switch (value.type) {
            case PropertyType.BINARY:
                result = value.binary
                break
            case PropertyType.BOOLEAN:
                result = value.boolean
                break
            case PropertyType.DATE:
                result = value.date
                break
            case PropertyType.DECIMAL:
                result = value.decimal
                break
            case PropertyType.DOUBLE:
                result = value.double
                break
            case PropertyType.LONG:
                result = value.long
                break
            case PropertyType.STRING:
                result = value.string
                break
            case PropertyType.REFERENCE:
                result = getNodeFromValue(session, value)
                break
            case PropertyType.WEAKREFERENCE:
                result = getNodeFromValue(session, value)
                break
            case PropertyType.URI:
                result = value.string
                break
            case PropertyType.PATH:
                result = value.string
        }

        result
    }

    private static Node getNodeFromValue(Session session, Value value) {
        def uuid = value.string

        uuid ? session.getNodeByIdentifier(uuid) : null
    }
}
