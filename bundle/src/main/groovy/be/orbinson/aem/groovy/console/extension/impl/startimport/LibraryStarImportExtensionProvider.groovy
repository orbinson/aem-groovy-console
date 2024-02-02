package be.orbinson.aem.groovy.console.extension.impl.startimport

import be.orbinson.aem.groovy.console.api.StarImport
import be.orbinson.aem.groovy.console.api.StarImportExtensionProvider
import org.osgi.service.component.annotations.Component

@Component
class LibraryStarImportExtensionProvider implements StarImportExtensionProvider {

    private static final Set<StarImport> IMPORTS = Set.of(
            new StarImport("be.orbinson.aem.groovy.console.library")
    )

    @Override
    Set<StarImport> getStarImports() {
        IMPORTS
    }
}
