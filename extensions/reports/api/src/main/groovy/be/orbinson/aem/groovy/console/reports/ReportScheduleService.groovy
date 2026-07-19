package be.orbinson.aem.groovy.console.reports

import be.orbinson.aem.groovy.console.reports.model.ReportDefinition
import org.osgi.annotation.versioning.ProviderType

/**
 * Keeps the set of registered cron schedules in sync with report definitions.  Backed by Sling's job scheduler,
 * so schedules persist across restarts and, in a cluster, fire on a single instance.
 */
@ProviderType
interface ReportScheduleService {

    /**
     * Reconcile the scheduled job for a report definition: (re)register it when the definition has an enabled
     * schedule and a valid cron expression, or remove it otherwise.  Keyed on the definition's node path, so it is
     * safe to call on every save and independent of the report name.
     *
     * @param reportDefinition report definition whose schedule to apply (its {@code path} must be set)
     */
    void reconcile(ReportDefinition reportDefinition)

    /**
     * Remove any scheduled job registered for the report definition at the given node path (e.g. on delete).
     *
     * @param reportPath definition node path
     */
    void unschedule(String reportPath)

    /**
     * Discover report definitions deployed in code under the immutable
     * {@link be.orbinson.aem.groovy.console.reports.constants.ReportsConstants#PATH_APPS_REPORTS_FOLDER} drop-zone
     * and synchronise their schedules: register jobs for enabled definitions and drop jobs for definitions that no
     * longer exist there.  Called on activation and whenever that tree changes (e.g. a content package install).
     */
    void reconcileDeployedReports()
}
