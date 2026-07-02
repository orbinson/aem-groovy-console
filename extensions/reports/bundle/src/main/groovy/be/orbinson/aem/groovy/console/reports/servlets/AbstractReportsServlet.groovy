package be.orbinson.aem.groovy.console.reports.servlets

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.SlingHttpServletResponse
import org.apache.sling.api.servlets.SlingAllMethodsServlet

import static be.orbinson.aem.groovy.console.reports.constants.ReportsConstants.CHARSET

@Slf4j("LOG")
abstract class AbstractReportsServlet extends SlingAllMethodsServlet {

    void writeJsonResponse(SlingHttpServletResponse response, json) {
        response.contentType = "application/json"
        response.characterEncoding = CHARSET

        new JsonBuilder(json).writeTo(response.writer)
    }

    void writeError(SlingHttpServletResponse response, int status, String message) {
        response.status = status

        writeJsonResponse(response, [error: message, status: status])
    }

    Map readJsonBody(SlingHttpServletRequest request) {
        try {
            def body = new JsonSlurper().parse(request.reader)

            body instanceof Map ? body : null
        } catch (Exception e) {
            LOG.debug("error parsing JSON request body", e)

            null
        }
    }
}
