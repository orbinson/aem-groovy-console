package be.orbinson.aem.groovy.console.components

import be.orbinson.aem.groovy.console.GroovyConsoleService
import be.orbinson.aem.groovy.console.audit.AuditService
import be.orbinson.aem.groovy.console.configuration.ConfigurationService
import be.orbinson.aem.groovy.console.impl.AEMDetector
import groovy.json.JsonBuilder
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.models.annotations.Model
import org.apache.sling.models.annotations.injectorspecific.InjectionStrategy
import org.apache.sling.models.annotations.injectorspecific.OSGiService
import org.apache.sling.models.annotations.injectorspecific.Self

import static be.orbinson.aem.groovy.console.constants.GroovyConsoleConstants.SCRIPT
import static be.orbinson.aem.groovy.console.constants.GroovyConsoleConstants.USER_ID

/**
 * Builds the bootstrap configuration (window.__GC_CONFIG__) consumed by the modern console SPA on load:
 * context path, AEM/permission/feature flags, active jobs, and any audit record requested via a deep link.
 */
@Model(adaptables = SlingHttpServletRequest)
class ModernConsoleConfig {

    @OSGiService
    private ConfigurationService configurationService

    @OSGiService
    private GroovyConsoleService groovyConsoleService

    @OSGiService
    private AuditService auditService

    @OSGiService(injectionStrategy = InjectionStrategy.OPTIONAL)
    private AEMDetector aemDetector

    @Self
    private SlingHttpServletRequest request

    String getJson() {
        def userId = request.getParameter(USER_ID)
        def script = request.getParameter(SCRIPT)
        def auditRecord = script ? auditService.getAuditRecord(userId, script) : null

        def activeJobs = groovyConsoleService.activeJobs.collect { activeJob ->
            [
                    id         : activeJob.id,
                    title      : activeJob.title,
                    description: activeJob.description,
                    script     : activeJob.script,
                    startTime  : activeJob.formattedStartTime
            ]
        }

        new JsonBuilder([
                contextPath                : request.contextPath,
                aem                        : aemDetector != null,
                hasScheduledJobPermission  : configurationService.hasScheduledJobPermission(request),
                auditEnabled               : !configurationService.auditDisabled,
                distributedExecutionEnabled: configurationService.distributedExecutionEnabled,
                emailEnabled               : configurationService.emailEnabled,
                activeJobs                 : activeJobs,
                classicUrl                 : "${request.contextPath}/apps/groovyconsole.classic.html" as String,
                groovyVersion              : GroovySystem.version,
                auditRecord                : auditRecord
        ]).toString()
    }
}
