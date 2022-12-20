package be.orbinson.aem.groovy.console.servlets

import be.orbinson.aem.groovy.console.constants.GroovyConsoleConstants
import com.google.common.net.MediaType
import groovy.json.JsonBuilder
import org.apache.sling.api.SlingHttpServletResponse
import org.apache.sling.api.servlets.SlingAllMethodsServlet

abstract class AbstractJsonResponseServlet extends SlingAllMethodsServlet {

    void writeJsonResponse(SlingHttpServletResponse response, json) {
        response.contentType = MediaType.JSON_UTF_8.withoutParameters().toString()
        response.characterEncoding = GroovyConsoleConstants.CHARSET

        new JsonBuilder(json).writeTo(response.writer)
    }
}
