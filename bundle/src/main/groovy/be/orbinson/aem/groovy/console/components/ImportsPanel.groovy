package be.orbinson.aem.groovy.console.components

import be.orbinson.aem.groovy.console.api.StarImport
import be.orbinson.aem.groovy.console.extension.ExtensionService
import org.apache.sling.api.resource.Resource
import org.apache.sling.models.annotations.Model
import org.apache.sling.models.annotations.injectorspecific.OSGiService

@Model(adaptables = Resource)
class ImportsPanel {

    @OSGiService
    private ExtensionService extensionService

    Set<StarImport> getStarImports() {
        extensionService.starImports as TreeSet
    }
}
