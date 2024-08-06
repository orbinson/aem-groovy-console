package be.orbinson.aem.groovy.console.extension.impl.startimport

import be.orbinson.aem.groovy.console.api.StarImport
import be.orbinson.aem.groovy.console.api.StarImportExtensionProvider
import org.osgi.service.component.annotations.Component

@Component(service = StarImportExtensionProvider, immediate = true)
class AemStarImportExtensionProvider implements StarImportExtensionProvider {

    private static final String AEM_JAVADOC_PREFIX = "https://developer.adobe.com/experience-manager/reference-materials/6-5/javadoc"

    private static final String JAVADOC_SUFFIX = "package-summary.html"

    private static final Set<StarImport> IMPORTS = Set.of(
            new StarImport("com.day.cq.dam.api", "$AEM_JAVADOC_PREFIX/com/day/cq/dam/api/$JAVADOC_SUFFIX"),
            new StarImport("com.day.cq.search", "$AEM_JAVADOC_PREFIX/com/day/cq/search/$JAVADOC_SUFFIX"),
            new StarImport("com.day.cq.tagging", "$AEM_JAVADOC_PREFIX/com/day/cq/tagging/$JAVADOC_SUFFIX"),
            new StarImport("com.day.cq.wcm.api", "$AEM_JAVADOC_PREFIX/com/day/cq/wcm/api/$JAVADOC_SUFFIX"),
            new StarImport("com.day.cq.replication", "$AEM_JAVADOC_PREFIX/com/day/cq/replication/$JAVADOC_SUFFIX"),
    )

    // TODO add check to see if it is an AEM system?

    @Override
    Set<StarImport> getStarImports() {
        IMPORTS
    }
}
