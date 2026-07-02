package be.orbinson.aem.groovy.console.migration.impl

import be.orbinson.aem.groovy.console.migration.MigrationRunOptions
import be.orbinson.aem.groovy.console.migration.MigrationService
import be.orbinson.aem.groovy.console.migration.MigrationStatus
import groovy.util.logging.Slf4j
import org.apache.sling.api.resource.ModifiableValueMap
import org.apache.sling.api.resource.ResourceResolverFactory
import org.apache.sling.event.jobs.Job
import org.apache.sling.event.jobs.consumer.JobConsumer
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import static be.orbinson.aem.groovy.console.migration.MigrationConstants.*

@Component(service = JobConsumer, immediate = true, property = [
        "job.topics=groovyconsole/migration"
])
@Slf4j("LOG")
class MigrationJobConsumer implements JobConsumer {

    @Reference
    private MigrationService migrationService

    @Reference
    private ResourceResolverFactory resourceResolverFactory

    @Override
    JobResult process(Job job) {
        def runId = job.getProperty(RUN_ID, String)

        LOG.info("processing migration job with run ID : {}", runId)

        try {
            migrationService.run(new MigrationRunOptions(
                    trigger: job.getProperty(PN_TRIGGER, TRIGGER_API),
                    dryRun: job.getProperty(DRY_RUN, false),
                    runId: runId
            ))
        } catch (Exception e) {
            LOG.error("error processing migration job with run ID : $runId", e)

            markRunFailed(runId, e)
        }

        // always return OK, migration failures are recorded in the run history and retried on the next trigger
        JobResult.OK
    }

    private void markRunFailed(String runId, Exception exception) {
        if (runId) {
            resourceResolverFactory.getServiceResourceResolver(null).withCloseable { resourceResolver ->
                def runResource = resourceResolver.getResource("$PATH_MIGRATION_RUNS/$runId")

                if (runResource) {
                    def valueMap = runResource.adaptTo(ModifiableValueMap)

                    valueMap.put(PN_STATUS, MigrationStatus.FAILED.name())
                    valueMap.put(PN_ERROR, exception.message ?: exception.class.name)
                    valueMap.put(PN_END_DATE, Calendar.instance)

                    resourceResolver.commit()
                }
            }
        }
    }
}
