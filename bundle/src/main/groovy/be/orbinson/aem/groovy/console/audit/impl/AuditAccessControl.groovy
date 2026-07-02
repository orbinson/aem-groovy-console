package be.orbinson.aem.groovy.console.audit.impl

import be.orbinson.aem.groovy.console.configuration.ConfigurationService
import be.orbinson.aem.groovy.console.constants.GroovyConsoleConstants
import org.apache.sling.api.SlingHttpServletRequest

/**
 * Ownership rules for accessing a single audit record. Audit records are read/written by the console's
 * {@code jcr:all}-on-{@code /} service user, so the servlets and page models that expose them by
 * {@code (userId, script)} must enforce access themselves — otherwise any authenticated user could read or
 * delete another user's records by supplying their user ID.
 */
class AuditAccessControl {

    private AuditAccessControl() {
    }

    /**
     * Whether the requesting user may access the audit record owned by {@code recordUserId}: their own records,
     * scheduled-job records when they hold the scheduled-job permission, or any record when the
     * "display all audit records" option is enabled. Requires the console permission in all cases.
     */
    static boolean canAccessAuditRecord(SlingHttpServletRequest request, String recordUserId,
                                        ConfigurationService configurationService) {
        if (!configurationService.hasPermission(request)) {
            return false
        }

        if (recordUserId && recordUserId == request.resourceResolver.userID) {
            return true
        }

        if (recordUserId == GroovyConsoleConstants.SYSTEM_USER_NAME) {
            return configurationService.hasScheduledJobPermission(request)
        }

        configurationService.displayAllAuditRecords
    }
}
