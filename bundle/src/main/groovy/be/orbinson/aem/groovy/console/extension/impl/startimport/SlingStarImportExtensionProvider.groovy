package be.orbinson.aem.groovy.console.extension.impl.startimport

import be.orbinson.aem.groovy.console.api.StarImport
import be.orbinson.aem.groovy.console.api.StarImportExtensionProvider
import org.osgi.service.component.annotations.Component

@Component(service = StarImportExtensionProvider, immediate = true)
class SlingStarImportExtensionProvider implements StarImportExtensionProvider {

    private static final String SLING_JAVADOC_PREFIX = "https://sling.apache.org/apidocs/sling12"

    private static final String JAVADOC_SUFFIX = "package-summary.html"

    private static final Set<StarImport> IMPORTS = [
            new StarImport("org.apache.sling.api", "$SLING_JAVADOC_PREFIX/org/apache/sling/api/$JAVADOC_SUFFIX"),
            new StarImport("org.apache.sling.api.resource", "$SLING_JAVADOC_PREFIX/org/apache/sling/api/resource/$JAVADOC_SUFFIX"),
            new StarImport("org.apache.sling.distribution", "$SLING_JAVADOC_PREFIX/org/apache/sling/api/distribution/$JAVADOC_SUFFIX")
    ] as Set

    @Override
    Set<StarImport> getStarImports() {
        IMPORTS
    }
}
