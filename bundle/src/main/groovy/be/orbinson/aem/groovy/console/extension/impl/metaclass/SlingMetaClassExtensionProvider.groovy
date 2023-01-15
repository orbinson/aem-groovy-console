package be.orbinson.aem.groovy.console.extension.impl.metaclass

import be.orbinson.aem.groovy.console.extension.MetaClassExtensionProvider
import com.google.common.base.Optional
import groovy.util.logging.Slf4j
import org.osgi.service.component.annotations.Component

import javax.servlet.ServletRequest

/**
 * This metaclass provider adds additional methods to all instances of the classes outlined below.
 * <p/>
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
class SlingMetaClassExtensionProvider implements MetaClassExtensionProvider {

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

    static def DEFAULT_METACLASSES = [
            (Optional)      : OPTIONAL_METACLASS,
            (ServletRequest): SERVLET_REQUEST_METACLASS
    ]

    @Override
    Map<Class, Closure> getMetaClasses() {
        DEFAULT_METACLASSES
    }

}
