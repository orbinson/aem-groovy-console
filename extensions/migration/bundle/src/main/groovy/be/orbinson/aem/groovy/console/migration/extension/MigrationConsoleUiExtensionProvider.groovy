package be.orbinson.aem.groovy.console.migration.extension

import be.orbinson.aem.groovy.console.api.ConsoleUiExtensionProvider
import org.osgi.service.component.annotations.Component

/**
 * Announces the migration panel module to the modern console UI.  The module registers a "Migrations" panel
 * in the console's activity rail via <code>window.GroovyConsole.registerPanel(...)</code>.  When the
 * migration bundle is not installed this service is absent and the console UI stays untouched.
 */
@Component(service = ConsoleUiExtensionProvider, immediate = true)
class MigrationConsoleUiExtensionProvider implements ConsoleUiExtensionProvider {

    private static final List<String> MODULE_URLS = ["/apps/groovyconsole-migration/spa/assets/migration-panel.js"].asImmutable()

    @Override
    List<String> getModuleUrls() {
        MODULE_URLS
    }
}
