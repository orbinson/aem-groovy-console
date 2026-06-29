package be.orbinson.aem.groovy.console.reports.impl

import groovy.transform.Synchronized
import groovy.util.logging.Slf4j
import org.apache.sling.api.resource.PersistenceException
import org.apache.sling.api.resource.Resource
import org.apache.sling.api.resource.ResourceResolver
import org.apache.sling.api.resource.ResourceResolverFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Modified
import org.osgi.service.component.annotations.Reference
import org.osgi.service.metatype.annotations.Designate

import static be.orbinson.aem.groovy.console.reports.constants.ReportsConstants.EXECUTION_NODE_PREFIX
import static be.orbinson.aem.groovy.console.reports.constants.ReportsConstants.PATH_EXECUTIONS_FOLDER

/**
 * Scheduled job purging old report executions according to the configured age and count limits.
 */
@Component(service = Runnable, immediate = true, property = [
        "scheduler.concurrent:Boolean=false"
])
@Designate(ocd = ReportExecutionPurgeProperties)
@Slf4j("LOG")
class ReportExecutionPurgeJob implements Runnable {

    @Reference
    private ResourceResolverFactory resourceResolverFactory

    private boolean enabled

    private int maxAgeDays

    private int maxCountPerReport

    @Activate
    @Modified
    @Synchronized
    void activate(ReportExecutionPurgeProperties properties) {
        enabled = properties.enabled()
        maxAgeDays = properties.maxAgeDays()
        maxCountPerReport = properties.maxCountPerReport()
    }

    @Override
    void run() {
        if (!enabled) {
            return
        }

        resourceResolverFactory.getServiceResourceResolver(null).withCloseable { ResourceResolver resourceResolver ->
            def executionsFolder = resourceResolver.getResource(PATH_EXECUTIONS_FOLDER)

            if (!executionsFolder) {
                return
            }

            def deleted = 0

            executionsFolder.listChildren().each { reportFolder ->
                deleted += purgeReport(resourceResolver, reportFolder)
            }

            try {
                if (resourceResolver.hasChanges()) {
                    resourceResolver.commit()
                }

                if (deleted) {
                    LOG.info("purged {} report executions", deleted)
                }
            } catch (PersistenceException e) {
                LOG.error("error purging report executions", e)
            }
        }
    }

    // internals

    private int purgeReport(ResourceResolver resourceResolver, Resource reportFolder) {
        def executions = []

        collectExecutions(reportFolder, executions)

        executions.sort { resource -> -(resource.valueMap.get("startedAt", Calendar)?.timeInMillis ?: 0) }

        def toDelete = [] as Set

        if (maxCountPerReport > 0 && executions.size() > maxCountPerReport) {
            toDelete.addAll(executions.subList(maxCountPerReport, executions.size()))
        }

        if (maxAgeDays > 0) {
            def cutoff = Calendar.instance

            cutoff.add(Calendar.DAY_OF_MONTH, -maxAgeDays)

            toDelete.addAll(executions.findAll { resource ->
                def startedAt = resource.valueMap.get("startedAt", Calendar)

                startedAt && startedAt.before(cutoff)
            })
        }

        toDelete.each { resource ->
            try {
                resourceResolver.delete(resource)
            } catch (PersistenceException e) {
                LOG.error("error deleting execution : {}", resource.path, e)
            }
        }

        toDelete.size()
    }

    private static void collectExecutions(Resource resource, List<Resource> executions) {
        resource.listChildren().each { child ->
            if (child.name.startsWith(EXECUTION_NODE_PREFIX)) {
                executions.add(child)
            } else {
                collectExecutions(child, executions)
            }
        }
    }
}
