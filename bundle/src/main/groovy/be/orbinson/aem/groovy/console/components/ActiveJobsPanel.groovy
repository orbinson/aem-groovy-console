package be.orbinson.aem.groovy.console.components


import be.orbinson.aem.groovy.console.GroovyConsoleService
import be.orbinson.aem.groovy.console.api.ActiveJob
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.models.annotations.Model
import org.apache.sling.models.annotations.injectorspecific.OSGiService

@Model(adaptables = SlingHttpServletRequest)
class ActiveJobsPanel {

    @OSGiService
    private GroovyConsoleService groovyConsoleService

    private List<ActiveJob> activeJobs

    List<ActiveJob> getActiveJobs() {
        if(activeJobs == null) {
            activeJobs = groovyConsoleService.activeJobs
        }
        activeJobs
    }
}
