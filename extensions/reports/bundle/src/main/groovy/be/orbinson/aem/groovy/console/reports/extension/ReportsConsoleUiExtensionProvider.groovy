package be.orbinson.aem.groovy.console.reports.extension

import be.orbinson.aem.groovy.console.api.ConsoleUiExtensionProvider
import org.osgi.service.component.annotations.Component

/**
 * Announces the reports panel module to the modern console UI.  The module registers a "Reports" panel in
 * the console's activity rail via <code>window.GroovyConsole.registerPanel(...)</code>.  When the reports
 * bundle is not installed this service is absent and the console UI stays untouched.
 */
@Component(service = ConsoleUiExtensionProvider, immediate = true)
class ReportsConsoleUiExtensionProvider implements ConsoleUiExtensionProvider {

    private static final List<String> MODULE_URLS = ["/apps/groovyconsole/spa/assets/reports-panel.js"].asImmutable()

    @Override
    List<String> getModuleUrls() {
        MODULE_URLS
    }
}
