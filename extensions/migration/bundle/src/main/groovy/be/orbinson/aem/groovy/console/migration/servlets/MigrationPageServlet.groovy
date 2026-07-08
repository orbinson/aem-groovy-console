package be.orbinson.aem.groovy.console.migration.servlets

import groovy.json.JsonBuilder
import groovy.util.logging.Slf4j
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.SlingHttpServletResponse
import org.apache.sling.api.servlets.SlingSafeMethodsServlet
import org.osgi.service.component.annotations.Component

import javax.servlet.Servlet
import javax.servlet.ServletException

import static be.orbinson.aem.groovy.console.constants.GroovyConsoleConstants.CHARSET

/**
 * Serves the migration history UI shell at <code>/apps/groovyconsole/migrations.html</code>.
 *
 * The page is a path-bound servlet (not JCR content) so it ships entirely with the migration bundle: it only
 * exists when the migration extension is installed and survives reinstalls of the console's content packages.
 */
@Component(service = Servlet, immediate = true, property = [
        "sling.servlet.paths=/apps/groovyconsole/migrations"
])
@Slf4j("LOG")
class MigrationPageServlet extends SlingSafeMethodsServlet {

    private static final String ASSETS_PATH = "/apps/groovyconsole-migration/spa/assets"

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
    <title>Migrations</title>
    <link rel="stylesheet" href="${contextPath}${ASSETS_PATH}/migration.css"/>
</head>
<body>
<gcm-app></gcm-app>
<script>window.__GC_CONFIG__ = ${configJson};</script>
<script type="module" src="${contextPath}${ASSETS_PATH}/migration.js"></script>
</body>
</html>
""")
    }
}
