package be.orbinson.aem.groovy.console.reports.servlets

import groovy.json.JsonBuilder
import groovy.util.logging.Slf4j
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.SlingHttpServletResponse
import org.apache.sling.api.servlets.SlingSafeMethodsServlet
import org.osgi.service.component.annotations.Component

import javax.servlet.Servlet
import javax.servlet.ServletException

import static be.orbinson.aem.groovy.console.reports.constants.ReportsConstants.CHARSET

/**
 * Serves the business-facing reports UI shell at <code>/apps/groovyconsole/reports.html</code>.
 *
 * The page is a path-bound servlet (not JCR content) so it ships entirely with the reports bundle: it only
 * exists when the reports extension is installed and survives reinstalls of the console's content packages.
 */
@Component(service = Servlet, immediate = true, property = [
        "sling.servlet.paths=/apps/groovyconsole/reports"
])
@Slf4j("LOG")
class ReportsPageServlet extends SlingSafeMethodsServlet {

    private static final String ASSETS_PATH = "/apps/groovyconsole/spa/assets"

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        def contextPath = request.contextPath ?: ""
        def configJson = new JsonBuilder([contextPath: contextPath]).toString()

        response.contentType = "text/html"
        response.characterEncoding = CHARSET

        response.writer.write("""\
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <title>Reports</title>
    <link rel="stylesheet" href="${contextPath}${ASSETS_PATH}/reports.css"/>
</head>
<body>
<gcr-app></gcr-app>
<script>window.__GC_CONFIG__ = ${configJson};</script>
<script type="module" src="${contextPath}${ASSETS_PATH}/reports.js"></script>
</body>
</html>
""")
    }
}
