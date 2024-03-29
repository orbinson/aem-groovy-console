package be.orbinson.aem.groovy.console.components


import be.orbinson.aem.groovy.console.GroovyConsoleService
import be.orbinson.aem.groovy.console.audit.AuditRecord
import be.orbinson.aem.groovy.console.audit.AuditService
import be.orbinson.aem.groovy.console.configuration.ConfigurationService
import groovy.json.JsonBuilder
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.models.annotations.Model
import org.apache.sling.models.annotations.injectorspecific.OSGiService
import org.apache.sling.models.annotations.injectorspecific.Self

import javax.annotation.PostConstruct

import static be.orbinson.aem.groovy.console.constants.GroovyConsoleConstants.SCRIPT
import static be.orbinson.aem.groovy.console.constants.GroovyConsoleConstants.USER_ID

@Model(adaptables = SlingHttpServletRequest)
class Body {

    @OSGiService
    private AuditService auditService

    @OSGiService
    private ConfigurationService configurationService

    @OSGiService
    private GroovyConsoleService groovyConsoleService

    @Self
    private SlingHttpServletRequest request

    private AuditRecord auditRecord

    @PostConstruct
    void init() {
        def userId = request.getParameter(USER_ID)
        def script = request.getParameter(SCRIPT)

        if (script) {
            auditRecord = auditService.getAuditRecord(userId, script)
        }
    }

    String getAuditRecordJson() {
        auditRecord ? new JsonBuilder(auditRecord).toString() : null
    }

    boolean isHasScheduledJobPermission() {
        configurationService.hasScheduledJobPermission(request)
    }

    boolean isHasActiveJobs() {
        groovyConsoleService.activeJobs
    }

    boolean isAuditEnabled() {
        !configurationService.auditDisabled
    }
}
