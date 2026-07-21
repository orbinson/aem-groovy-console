package be.orbinson.aem.groovy.console.reports.impl

import be.orbinson.aem.groovy.console.reports.ReportScheduleService
import groovy.util.logging.Slf4j
import org.apache.sling.api.resource.observation.ResourceChange
import org.apache.sling.api.resource.observation.ResourceChangeListener
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Re-synchronises code-deployed report schedules when the immutable
 * {@link be.orbinson.aem.groovy.console.reports.constants.ReportsConstants#PATH_APPS_REPORTS_FOLDER} tree changes,
 * e.g. a content package installing or removing report definitions after startup.  Bursts of change events (a
 * package install touches many nodes) are debounced into a single reconcile.
 */
@Component(property = [
        "resource.paths=glob:/apps/groovyconsole-reports-definitions/**",
        "resource.change.types=ADDED",
        "resource.change.types=CHANGED",
        "resource.change.types=REMOVED"
])
@Slf4j("LOG")
class AppsReportDefinitionListener implements ResourceChangeListener {

    private static final long DEBOUNCE_MILLIS = 2000

    @Reference
    private ReportScheduleService scheduleService

    private ScheduledExecutorService executor

    private ScheduledFuture<?> pendingReconcile

    @Activate
    synchronized void activate() {
        executor = Executors.newSingleThreadScheduledExecutor()
    }

    @Deactivate
    synchronized void deactivate() {
        executor?.shutdownNow()
        executor = null
    }

    @Override
    synchronized void onChange(List<ResourceChange> changes) {
        if (executor == null) {
            return
        }

        LOG.debug("detected {} code-deployed report definition change(s), scheduling reconcile", changes.size())

        // coalesce event bursts into a single deferred reconcile
        pendingReconcile?.cancel(false)

        pendingReconcile = executor.schedule({
            try {
                scheduleService.reconcileDeployedReports()
            } catch (Exception e) {
                LOG.error("error reconciling code-deployed reports after change", e)
            }
        } as Runnable, DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS)
    }
}
