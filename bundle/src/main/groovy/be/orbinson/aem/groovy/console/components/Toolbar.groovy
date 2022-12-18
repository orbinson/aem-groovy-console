package be.orbinson.aem.groovy.console.components

import be.orbinson.aem.groovy.console.configuration.ConfigurationService
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.models.annotations.Model
import org.apache.sling.models.annotations.injectorspecific.OSGiService

@Model(adaptables = SlingHttpServletRequest)
class Toolbar {
    @OSGiService
    private ConfigurationService configurationService

    boolean isDistributedExecutionEnabled() {
        configurationService.distributedExecutionEnabled
    }
}
