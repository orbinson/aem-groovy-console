package org.cid15.aem.groovy.console.components

import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.models.annotations.Model
import org.apache.sling.models.annotations.injectorspecific.OSGiService
import org.cid15.aem.groovy.console.configuration.ConfigurationService

@Model(adaptables = SlingHttpServletRequest)
class Toolbar {
    @OSGiService
    private ConfigurationService configurationService

    boolean isDistributedExecutionEnabled() {
        configurationService.distributedExecutionEnabled
    }
}
