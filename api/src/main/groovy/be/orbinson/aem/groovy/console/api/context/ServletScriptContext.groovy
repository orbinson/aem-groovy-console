package be.orbinson.aem.groovy.console.api.context

import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.SlingHttpServletResponse
import org.osgi.annotation.versioning.ConsumerType

/**
 * Script context for scripts executed by a servlet (e.g. the default POST servlet execution).
 */
@ConsumerType
interface ServletScriptContext extends ScriptContext {

    /**
     * Get the servlet request.
     *
     * @return request
     */
    SlingHttpServletRequest getRequest()

    /**
     * Get the servlet response.
     *
     * @return response
     */
    SlingHttpServletResponse getResponse()
}
