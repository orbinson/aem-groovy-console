package be.orbinson.aem.groovy.console.extension.impl.metaclass

import be.orbinson.aem.groovy.console.extension.MetaClassExtensionProvider
import com.day.cq.wcm.api.Page
import com.day.cq.wcm.api.PageManagerFactory
import groovy.util.logging.Slf4j
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import javax.jcr.Node

/**
 * This metaclass provider adds additional methods to all instances of the classes outlined below.
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
 */
@Component(service = MetaClassExtensionProvider, immediate = true)
@Slf4j("LOG")
class AemMetaClassExtensionProvider implements MetaClassExtensionProvider {

    // Reference to make sure that this class does not become active when running in Sling instead of AEM
    @Reference
    private PageManagerFactory pageManagerFactory;

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
            (Page): PAGE_METACLASS
    ]

    @Override
    Map<Class, Closure> getMetaClasses() {
        DEFAULT_METACLASSES
    }

}
