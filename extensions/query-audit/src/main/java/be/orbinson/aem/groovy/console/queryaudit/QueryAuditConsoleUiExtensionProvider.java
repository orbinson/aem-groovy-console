package be.orbinson.aem.groovy.console.queryaudit;

import java.util.Collections;
import java.util.List;

import org.osgi.service.component.annotations.Component;

import be.orbinson.aem.groovy.console.api.ConsoleUiExtensionProvider;

/**
 * Announces the query-audit panel module to the modern console UI. The module (served by
 * {@link QueryAuditPanelServlet}) registers a "Query audit" panel in the console's activity rail via
 * {@code window.GroovyConsole.registerPanel(...)}. When the query-audit extension is not installed this service is
 * absent and the console UI stays untouched.
 */
@Component(service = ConsoleUiExtensionProvider.class, immediate = true)
public class QueryAuditConsoleUiExtensionProvider implements ConsoleUiExtensionProvider {

    private static final List<String> MODULE_URLS =
            Collections.singletonList("/bin/groovyconsole/query-audit-panel.js");

    @Override
    public List<String> getModuleUrls() {
        return MODULE_URLS;
    }
}
