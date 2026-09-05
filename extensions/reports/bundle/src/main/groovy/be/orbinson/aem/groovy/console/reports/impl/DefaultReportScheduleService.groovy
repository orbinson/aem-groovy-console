package be.orbinson.aem.groovy.console.reports.impl

import be.orbinson.aem.groovy.console.reports.ReportScheduleService
import be.orbinson.aem.groovy.console.reports.ReportService
import be.orbinson.aem.groovy.console.reports.model.ReportDefinition
import groovy.transform.Synchronized
import groovy.util.logging.Slf4j
import org.apache.sling.api.resource.ResourceResolverFactory
import org.apache.sling.event.jobs.JobManager
import org.apache.sling.event.jobs.ScheduledJobInfo
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import static be.orbinson.aem.groovy.console.reports.constants.ReportsConstants.JOB_PROPERTY_REPORT_NAME
import static be.orbinson.aem.groovy.console.reports.constants.ReportsConstants.JOB_PROPERTY_REPORT_PATH
import static be.orbinson.aem.groovy.console.reports.constants.ReportsConstants.JOB_TOPIC
import static be.orbinson.aem.groovy.console.reports.constants.ReportsConstants.PATH_APPS_REPORTS_FOLDER

/**
 * Keeps Sling scheduled jobs in sync with report definitions.  Each scheduled report maps to exactly one
 * scheduled job on {@link be.orbinson.aem.groovy.console.reports.constants.ReportsConstants#JOB_TOPIC}, keyed on
 * the definition's node path and carrying only that path; the consumer re-reads the (current) definition at run
 * time.  Definitions authored in the UI live under <code>/conf</code>; definitions deployed in code under the
 * immutable {@link #PATH_APPS_REPORTS_FOLDER} drop-zone are discovered on activation and whenever that tree
 * changes.
 */
@Component(service = ReportScheduleService, immediate = true)
@Slf4j("LOG")
class DefaultReportScheduleService implements ReportScheduleService {

    @Reference
    private JobManager jobManager

    @Reference
    private ResourceResolverFactory resourceResolverFactory

    @Reference
    private ReportService reportService

    @Reference
    private ReportsConfigurationService reportsConfigurationService

    @Activate
    void activate() {
        reconcileDeployedReports()
    }

    @Override
    @Synchronized
    void reconcile(ReportDefinition reportDefinition) {
        unschedule(reportDefinition.path)

        // scheduling disabled globally (OSGi): never register a job; the unschedule above also drops any that
        // survived from when it was enabled
        if (!reportsConfigurationService.schedulingEnabled) {
            return
        }

        def schedule = reportDefinition.schedule

        if (!schedule?.enabled || !schedule.cronExpression?.trim()) {
            return
        }

        try {
            CronValidator.validate(schedule.cronExpression)
        } catch (IllegalArgumentException e) {
            LOG.error("not scheduling report {}: {}", reportDefinition.path, e.message)

            return
        }

        def errors = []

        def result = jobManager.createJob(JOB_TOPIC)
                .properties([
                        (JOB_PROPERTY_REPORT_PATH): reportDefinition.path,
                        (JOB_PROPERTY_REPORT_NAME): reportDefinition.name
                ] as Map<String, Object>)
                .schedule()
                .cron(schedule.cronExpression)
                .add(errors)

        if (result) {
            LOG.info("scheduled report {} with cron {}", reportDefinition.path, schedule.cronExpression)
        } else {
            LOG.error("failed to schedule report {} with cron {}: {}", reportDefinition.path,
                    schedule.cronExpression, errors.join("; "))
        }
    }

    @Override
    @Synchronized
    void unschedule(String reportPath) {
        scheduledJobsFor(reportPath).each { scheduledJob ->
            scheduledJob.unschedule()

            LOG.info("unscheduled report {}", reportPath)
        }
    }

    @Override
    @Synchronized
    void reconcileDeployedReports() {
        // scheduling disabled globally (OSGi): drop every report job (UI- and code-authored alike) and stop
        if (!reportsConfigurationService.schedulingEnabled) {
            def scheduledJobs = jobManager.getScheduledJobs(JOB_TOPIC, 0, null)

            scheduledJobs.each { scheduledJob -> scheduledJob.unschedule() }

            if (scheduledJobs) {
                LOG.info("scheduling disabled; unscheduled {} report job(s)", scheduledJobs.size())
            }

            return
        }

        try {
            resourceResolverFactory.getServiceResourceResolver(null).withCloseable { resolver ->
                def deployed = reportService.findReports(resolver, PATH_APPS_REPORTS_FOLDER)
                def deployedPaths = deployed*.path as Set

                // drop jobs for code-deployed definitions that no longer exist (e.g. uninstalled package)
                jobManager.getScheduledJobs(JOB_TOPIC, 0, null).each { scheduledJob ->
                    def path = scheduledJob.jobProperties[JOB_PROPERTY_REPORT_PATH] as String

                    if (path?.startsWith("$PATH_APPS_REPORTS_FOLDER/") && !deployedPaths.contains(path)) {
                        scheduledJob.unschedule()

                        LOG.info("unscheduled removed code-deployed report {}", path)
                    }
                }

                deployed.each { definition -> reconcile(definition) }

                LOG.info("reconciled {} code-deployed report definition(s) under {}", deployed.size(),
                        PATH_APPS_REPORTS_FOLDER)
            }
        } catch (Exception e) {
            LOG.error("error reconciling code-deployed reports under {}", PATH_APPS_REPORTS_FOLDER, e)
        }
    }

    private Collection<ScheduledJobInfo> scheduledJobsFor(String reportPath) {
        jobManager.getScheduledJobs(JOB_TOPIC, 0, null)
                .findAll { scheduledJob -> scheduledJob.jobProperties[JOB_PROPERTY_REPORT_PATH] == reportPath }
    }
}
