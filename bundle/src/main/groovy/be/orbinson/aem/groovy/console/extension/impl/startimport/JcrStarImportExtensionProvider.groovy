package be.orbinson.aem.groovy.console.extension.impl.startimport

import be.orbinson.aem.groovy.console.api.StarImport
import be.orbinson.aem.groovy.console.api.StarImportExtensionProvider
import org.osgi.service.component.annotations.Component

@Component(service = StarImportExtensionProvider, immediate = true)
class JcrStarImportExtensionProvider implements StarImportExtensionProvider {

    private static final String JCR_JAVADOC_PREFIX = "https://developer.adobe.com/experience-manager/reference-materials/spec/javax.jcr/javadocs/jcr-2.0"

    private static final String JAVADOC_SUFFIX = "package-summary.html"

    private static final Set<StarImport> IMPORTS = Set.of(
            new StarImport("javax.jcr", "$JCR_JAVADOC_PREFIX/javax/jcr/$JAVADOC_SUFFIX")
    )

    @Override
    Set<StarImport> getStarImports() {
        IMPORTS
    }
}
