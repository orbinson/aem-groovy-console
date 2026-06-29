package be.orbinson.aem.groovy.console.api

import org.osgi.annotation.versioning.ConsumerType

/**
 * Services may implement this interface to extend the modern console UI.  The provided ES module URLs are
 * loaded by the console SPA at startup; each module can register additional panels in the console's activity
 * rail via the <code>window.GroovyConsole.registerPanel(...)</code> API.
 *
 * This keeps UI extensions fully decoupled: the console works without any providers, and extensions (such as
 * the reports extension) hook in by registering an OSGi service from their own bundle.
 */
@ConsumerType
interface ConsoleUiExtensionProvider {

    /**
     * Get the URLs of the ES modules to load, relative to the servlet context path
     * (e.g. <code>/apps/groovyconsole-reports/spa/assets/reports-panel.js</code>).
     *
     * @return list of module URLs
     */
    List<String> getModuleUrls()
}
