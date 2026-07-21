package be.orbinson.aem.groovy.console.reports.servlets

import be.orbinson.aem.groovy.console.api.AbstractConsoleUiPageServlet
import org.osgi.service.component.annotations.Component

import javax.servlet.Servlet

/**
 * Serves the business-facing reports UI shell at <code>/apps/groovyconsole/reports.html</code>.
 *
 * The page is a path-bound servlet (not JCR content) so it ships entirely with the reports bundle: it only
 * exists when the reports extension is installed and survives reinstalls of the console's content packages.
 */
@Component(service = Servlet, immediate = true, property = [
        "sling.servlet.paths=/apps/groovyconsole/reports"
])
class ReportsPageServlet extends AbstractConsoleUiPageServlet {

    @Override
    protected String getPageTitle() {
        "Reports"
    }

    @Override
    protected String getAssetsPath() {
        "/apps/groovyconsole-reports/spa/assets"
    }

    @Override
    protected String getAppElement() {
        "gcr-app"
    }

    @Override
    protected String getAssetName() {
        "reports"
    }
}
