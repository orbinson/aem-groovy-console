package be.orbinson.aem.groovy.console.migration.servlets

import be.orbinson.aem.groovy.console.api.AbstractConsoleUiPageServlet
import org.osgi.service.component.annotations.Component

import javax.servlet.Servlet

/**
 * Serves the migration history UI shell at <code>/apps/groovyconsole/migrations.html</code>.
 *
 * The page is a path-bound servlet (not JCR content) so it ships entirely with the migration bundle: it only
 * exists when the migration extension is installed and survives reinstalls of the console's content packages.
 */
@Component(service = Servlet, immediate = true, property = [
        "sling.servlet.paths=/apps/groovyconsole/migrations"
])
class MigrationPageServlet extends AbstractConsoleUiPageServlet {

    @Override
    protected String getPageTitle() {
        "Migrations"
    }

    @Override
    protected String getAssetsPath() {
        "/apps/groovyconsole-migration/spa/assets"
    }

    @Override
    protected String getAppElement() {
        "gcm-app"
    }

    @Override
    protected String getAssetName() {
        "migration"
    }
}
