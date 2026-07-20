package be.orbinson.aem.groovy.console.components

import static be.orbinson.aem.groovy.console.constants.GroovyConsoleConstants.SCRIPT
import static be.orbinson.aem.groovy.console.constants.GroovyConsoleConstants.USER_ID

import be.orbinson.aem.groovy.console.GroovyConsoleService
import be.orbinson.aem.groovy.console.api.ConsoleUiExtensionProvider
import be.orbinson.aem.groovy.console.api.SpaManifest
import be.orbinson.aem.groovy.console.audit.AuditService
import be.orbinson.aem.groovy.console.configuration.ConfigurationService
import be.orbinson.aem.groovy.console.impl.AEMDetector
import groovy.json.JsonBuilder
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.models.annotations.Model
import org.apache.sling.models.annotations.injectorspecific.InjectionStrategy
import org.apache.sling.models.annotations.injectorspecific.OSGiService
import org.apache.sling.models.annotations.injectorspecific.Self

/**
 * Builds the bootstrap configuration (window.__GC_CONFIG__) consumed by the modern console SPA on load:
 * context path, AEM/permission/feature flags, active jobs, and any audit record requested via a deep link.
 */
@Model(adaptables = SlingHttpServletRequest)
class ModernConsoleConfig {

    private static final String CORE_SPA_BASE = "/apps/groovyconsole/spa"

    @OSGiService
    private ConfigurationService configurationService

    @OSGiService
    private GroovyConsoleService groovyConsoleService

    @OSGiService
    private AuditService auditService

    @OSGiService(injectionStrategy = InjectionStrategy.OPTIONAL)
    private AEMDetector aemDetector

    @OSGiService(injectionStrategy = InjectionStrategy.OPTIONAL)
    private List<ConsoleUiExtensionProvider> uiExtensionProviders

    @Self
    private SlingHttpServletRequest request

    String getJson() {
        def userId = request.getParameter(USER_ID)
        def script = request.getParameter(SCRIPT)
        def auditRecord = script ? auditService.getAuditRecord(request, userId, script) : null

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
                auditRecord                : auditRecord,
                uiExtensions               : (uiExtensionProviders ?: []).collectMany { provider ->
                    provider.moduleUrls ?: []
                }.collect { url -> SpaManifest.resolveModuleUrl(request.resourceResolver, url as String) }
        ]).toString()
    }

    /** Content-hashed console entry script (from the Vite manifest), for the HTL page to link. */
    String getScriptSrc() {
        def entry = SpaManifest.entry(request.resourceResolver, CORE_SPA_BASE, "index")
        def js = entry.js ?: "${CORE_SPA_BASE}/assets/index.js"

        "${request.contextPath}${js}"
    }

    /** Content-hashed console stylesheets (Monaco chunk CSS + entry CSS), for the HTL page to link. */
    List<String> getStyleSheets() {
        def resolver = request.resourceResolver
        def entry = SpaManifest.entry(resolver, CORE_SPA_BASE, "index")

        def sheets
        if (entry.js) {
            def monaco = SpaManifest.entry(resolver, CORE_SPA_BASE, "monaco")
            sheets = (monaco.css ?: []) + (entry.css ?: [])
        } else {
            sheets = ["${CORE_SPA_BASE}/assets/monaco.css".toString(), "${CORE_SPA_BASE}/assets/index.css".toString()]
        }

        sheets.collect { sheet -> "${request.contextPath}${sheet}".toString() }
    }
}
