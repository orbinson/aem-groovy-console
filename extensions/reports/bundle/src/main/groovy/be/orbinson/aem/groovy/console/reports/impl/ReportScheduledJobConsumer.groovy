package be.orbinson.aem.groovy.console.reports.impl

import be.orbinson.aem.groovy.console.reports.ReportExecutionService
import be.orbinson.aem.groovy.console.reports.ReportService
import groovy.util.logging.Slf4j
import org.apache.sling.api.resource.ResourceResolverFactory
import org.apache.sling.event.jobs.Job
import org.apache.sling.event.jobs.consumer.JobConsumer
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import static be.orbinson.aem.groovy.console.reports.constants.ReportsConstants.JOB_PROPERTY_REPORT_NAME
import static be.orbinson.aem.groovy.console.reports.constants.ReportsConstants.JOB_PROPERTY_REPORT_PATH

/**
 * Runs a scheduled report when its cron job fires.  Reads the current definition, impersonates the authorized
 * run-as user (see {@link ReportImpersonation}) and starts the execution with the report's configured
 * distributions applied on completion.
 */
@Component(service = JobConsumer, property = ["job.topics=groovyconsole/reports/job"])
@Slf4j("LOG")
class ReportScheduledJobConsumer implements JobConsumer {

    @Reference
    private ResourceResolverFactory resourceResolverFactory

    @Reference
    private ReportService reportService

    @Reference
    private ReportExecutionService reportExecutionService

    @Reference
    private ReportsConfigurationService reportsConfigurationService

    @Override
    JobConsumer.JobResult process(Job job) {
        def reportPath = job.getProperty(JOB_PROPERTY_REPORT_PATH, String)
        def reportName = job.getProperty(JOB_PROPERTY_REPORT_NAME, String)

        // scheduling disabled globally (OSGi): skip any job that still fires (jobs are unregistered on startup,
        // but a job could be mid-flight when the config is toggled)
        if (!reportsConfigurationService.schedulingEnabled) {
            LOG.info("scheduling is disabled; skipping scheduled report {}", reportName)

            return JobConsumer.JobResult.OK
        }

        if (!reportPath) {
            LOG.error("scheduled report job has no report path; cancelling")

            return JobConsumer.JobResult.CANCEL
        }

        try {
            resourceResolverFactory.getServiceResourceResolver(null).withCloseable { serviceResolver ->
                def definition = reportService.getReportAtPath(serviceResolver, reportPath)

                if (!definition) {
                    LOG.warn("scheduled report {} no longer exists; cancelling", reportPath)

                    return JobConsumer.JobResult.CANCEL
                }

                def schedule = definition.schedule

                if (!schedule?.enabled) {
                    LOG.info("scheduled report {} is no longer enabled; skipping", reportName)

                    return JobConsumer.JobResult.OK
                }

                // run under the executor service user; a configured runAs (UI schedules always set their author)
                // is impersonated so the run is bounded by that user's own permissions
                def executorResolver = ReportImpersonation.executorResolver(resourceResolverFactory)

                executorResolver.withCloseable {
                    def runResolver = ReportImpersonation.runResolver(executorResolver, schedule.runAs)
                    def runAsUserId = runResolver.userID

                    try {
                        reportExecutionService.execute(definition, schedule.parameterValues, runResolver,
                                definition.distributions)
                    } finally {
                        if (ReportImpersonation.isImpersonated(executorResolver, runResolver)) {
                            runResolver.close()
                        }
                    }

                    LOG.info("started scheduled execution of report {} as {}", reportName, runAsUserId)
                }

                JobConsumer.JobResult.OK
            }
        } catch (Exception e) {
            LOG.error("error running scheduled report {}", reportName, e)

            JobConsumer.JobResult.FAILED
        }
    }
}
