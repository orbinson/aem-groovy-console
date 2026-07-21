package be.orbinson.aem.groovy.console.reports.impl

import be.orbinson.aem.groovy.console.reports.ReportException
import groovy.util.logging.Slf4j
import org.apache.jackrabbit.api.JackrabbitSession
import org.apache.jackrabbit.api.security.user.Authorizable
import org.apache.jackrabbit.api.security.user.User
import org.apache.sling.api.resource.LoginException
import org.apache.sling.api.resource.ResourceResolver
import org.apache.sling.api.resource.ResourceResolverFactory

import javax.jcr.Session

import static be.orbinson.aem.groovy.console.reports.constants.ReportsConstants.EXECUTOR_SUBSERVICE

/**
 * Run-as handling for scheduled reports.
 *
 * <p>Scheduled reports execute under the dedicated <em>executor</em> service user (a separate identity from the
 * bookkeeping service user, with its own read ACLs — see the reports repoinit). A report deployed in code with no
 * {@code runAs} simply runs as that executor. A report scheduled through the UI always names its own author as
 * {@code runAs}; the executor then impersonates that user so the run is bounded by exactly the author's
 * permissions.
 *
 * <p>That impersonation is enabled by {@link #grantSelfImpersonation}, called when the schedule is saved: a user
 * may grant a system user impersonation over <em>their own</em> account (verified on Oak), so no privileged
 * user-administration service is needed and a user can only ever enable running as themselves.
 */
@Slf4j("LOG")
class ReportImpersonation {

    private ReportImpersonation() {
    }

    /** Open a resolver for the executor service user (the scheduled-report execution identity). */
    static ResourceResolver executorResolver(ResourceResolverFactory resourceResolverFactory) {
        resourceResolverFactory.getServiceResourceResolver(
                [(ResourceResolverFactory.SUBSERVICE): EXECUTOR_SUBSERVICE] as Map<String, Object>)
    }

    /**
     * Resolve the resolver a scheduled report should run under: the executor itself when no {@code runAs} is
     * configured, otherwise the executor impersonating {@code runAs}.  A distinct impersonated resolver is owned
     * by the caller and must be closed (see {@link #isImpersonated}).
     *
     * @throws ReportException when {@code runAs} is set but the executor may not impersonate it (e.g. the
     *         self-grant is missing)
     */
    static ResourceResolver runResolver(ResourceResolver executorResolver, String runAs) {
        if (!runAs?.trim() || runAs == executorResolver.userID) {
            return executorResolver
        }

        try {
            executorResolver.clone([(ResourceResolverFactory.USER_IMPERSONATION): runAs] as Map<String, Object>)
        } catch (LoginException e) {
            throw new ReportException("the reports executor is not permitted to run reports as ${runAs}; " +
                    "the schedule must be re-saved by that user to grant impersonation", e)
        }
    }

    /** Whether {@code runResolver} produced a distinct impersonated resolver the caller must close. */
    static boolean isImpersonated(ResourceResolver executorResolver, ResourceResolver runResolver) {
        !executorResolver.is(runResolver)
    }

    /**
     * Grant the executor system user impersonation over the resolver's own user, so scheduled runs can later run
     * as that user.  Idempotent and self-scoped: a user can only ever grant impersonation over themselves, so
     * this cannot be used to run as anyone else.  Best-effort — a failure is logged, not thrown, so saving a
     * report never fails on impersonation setup (the scheduled run fails closed later if the grant is missing).
     *
     * @param resourceResolver the requesting (schedule author's) resolver
     * @param executorUserId the executor system user id
     */
    static void grantSelfImpersonation(ResourceResolver resourceResolver, String executorUserId) {
        def session = resourceResolver.adaptTo(Session)

        if (!(session instanceof JackrabbitSession)) {
            LOG.warn("cannot grant executor impersonation: no Jackrabbit session available")

            return
        }

        try {
            def userManager = ((JackrabbitSession) session).userManager
            Authorizable executor = userManager.getAuthorizable(executorUserId)
            Authorizable self = userManager.getAuthorizable(resourceResolver.userID)

            if (executor == null || !(self instanceof User)) {
                LOG.warn("cannot grant executor impersonation: executor '{}' or user '{}' not found",
                        executorUserId, resourceResolver.userID)

                return
            }

            if (((User) self).impersonation.grantImpersonation(executor.principal)) {
                session.save()

                LOG.info("granted executor {} impersonation over {}", executorUserId, resourceResolver.userID)
            }
        } catch (Exception e) {
            LOG.warn("could not grant executor impersonation over {}; scheduled runs as this user will fail " +
                    "until impersonation is configured", resourceResolver.userID, e)
        }
    }
}
