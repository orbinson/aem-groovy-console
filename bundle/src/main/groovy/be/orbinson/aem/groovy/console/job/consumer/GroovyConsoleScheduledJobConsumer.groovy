package be.orbinson.aem.groovy.console.job.consumer

import be.orbinson.aem.groovy.console.GroovyConsoleService
import be.orbinson.aem.groovy.console.api.JobProperties
import be.orbinson.aem.groovy.console.api.context.impl.ScheduledJobScriptContext
import groovy.util.logging.Slf4j
import org.apache.sling.api.resource.ResourceResolverFactory
import org.apache.sling.event.jobs.Job
import org.apache.sling.event.jobs.consumer.JobConsumer
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import java.nio.charset.StandardCharsets

@Component(service = JobConsumer, immediate = true, property = [
        "job.topics=groovyconsole/job"
])
@Slf4j("LOG")
class GroovyConsoleScheduledJobConsumer implements JobConsumer {

    @Reference
    private ResourceResolverFactory resourceResolverFactory

    @Reference
    private GroovyConsoleService groovyConsoleService

    @Override
    JobResult process(Job job) {
        LOG.debug("executing groovy console job with properties : {}", job.propertyNames.collectEntries { propertyName ->
            [propertyName, job.getProperty(propertyName)]
        })

        resourceResolverFactory.getServiceResourceResolver(null).withCloseable { resourceResolver ->
            def outputStream = new ByteArrayOutputStream()

            def scriptContext = new ScheduledJobScriptContext(
                    resourceResolver: resourceResolver,
                    outputStream: outputStream,
                    printStream: new PrintStream(outputStream, true, StandardCharsets.UTF_8.name()),
                    jobId: job.id,
                    jobProperties: JobProperties.fromJob(job)
            )

            groovyConsoleService.runScript(scriptContext)

            JobResult.OK
        }
    }
}
