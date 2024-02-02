package be.orbinson.aem.groovy.console.impl

import be.orbinson.aem.groovy.console.GroovyConsoleService
import be.orbinson.aem.groovy.console.api.ActiveJob
import be.orbinson.aem.groovy.console.api.JobProperties
import be.orbinson.aem.groovy.console.api.context.ScriptContext
import be.orbinson.aem.groovy.console.api.context.ScriptData
import be.orbinson.aem.groovy.console.audit.AuditService
import be.orbinson.aem.groovy.console.configuration.ConfigurationService
import be.orbinson.aem.groovy.console.constants.GroovyConsoleConstants
import be.orbinson.aem.groovy.console.extension.ExtensionService
import be.orbinson.aem.groovy.console.library.impl.LibraryCompilationCustomizer
import be.orbinson.aem.groovy.console.notification.NotificationService
import be.orbinson.aem.groovy.console.response.RunScriptResponse
import be.orbinson.aem.groovy.console.response.SaveScriptResponse
import be.orbinson.aem.groovy.console.response.impl.DefaultRunScriptResponse
import be.orbinson.aem.groovy.console.response.impl.DefaultSaveScriptResponse
import com.google.common.net.MediaType
import groovy.transform.Synchronized
import groovy.transform.TimedInterrupt
import groovy.util.logging.Slf4j
import org.apache.jackrabbit.JcrConstants
import org.apache.jackrabbit.util.Text
import org.apache.sling.api.resource.ModifiableValueMap
import org.apache.sling.api.resource.Resource
import org.apache.sling.api.resource.ResourceResolver
import org.apache.sling.api.resource.ResourceUtil
import org.apache.sling.event.jobs.JobManager
import org.apache.sling.jcr.resource.api.JcrResourceConstants
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer
import org.codehaus.groovy.control.customizers.CompilationCustomizer
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicy

import java.util.concurrent.CopyOnWriteArrayList

import static be.orbinson.aem.groovy.console.constants.GroovyConsoleConstants.*

@Component(service = GroovyConsoleService, immediate = true)
@Slf4j("LOG")
class DefaultGroovyConsoleService implements GroovyConsoleService {


    @Reference
    private ConfigurationService configurationService

    private volatile List<NotificationService> notificationServices = new CopyOnWriteArrayList<>()

    @Reference
    private AuditService auditService

    @Reference
    private ExtensionService extensionService

    @Reference
    private JobManager jobManager

    @Override
    RunScriptResponse runScript(ScriptContext scriptContext) {
        def binding = getBinding(scriptContext)

        def runScriptResponse = null

        try {
            def libraryCompilationCustomizer = new LibraryCompilationCustomizer()
            def configuration = getConfiguration(libraryCompilationCustomizer);
            def shell = new GroovyShell(binding, configuration)
            loadLibraries(shell, libraryCompilationCustomizer.getLibraries())

            def script = shell.parse(scriptContext.script)

            extensionService.getScriptMetaClasses(scriptContext).each { meta ->
                script.metaClass(meta)
            }

            def start = System.currentTimeMillis()
            def result = script.run()
            def date = new Date()

            date.time = System.currentTimeMillis() - start
            def runningTime = date.format(FORMAT_RUNNING_TIME, TimeZone.getTimeZone(TIME_ZONE_RUNNING_TIME))

            LOG.debug("script execution completed, running time : {}", runningTime)

            runScriptResponse = DefaultRunScriptResponse.fromResult(scriptContext, result,
                    scriptContext.outputStream.toString(CHARSET), runningTime)

            auditAndNotify(runScriptResponse)
        } catch (MultipleCompilationErrorsException e) {
            LOG.error("script compilation error", e)

            runScriptResponse = DefaultRunScriptResponse.fromException(scriptContext,
                    scriptContext.outputStream.toString(CHARSET), e)
        } catch (Throwable t) {
            LOG.error("error running script", t)

            runScriptResponse = DefaultRunScriptResponse.fromException(scriptContext,
                    scriptContext.outputStream.toString(CHARSET), t)

            auditAndNotify(runScriptResponse)
        } finally {
            scriptContext.outputStream.close()
        }

        runScriptResponse
    }

    @Override
    @Synchronized
    SaveScriptResponse saveScript(ScriptData scriptData) {
        def resourceResolver = scriptData.resourceResolver

        def folderResource = ResourceUtil.getOrCreateResource(resourceResolver, PATH_SCRIPTS_FOLDER, JcrResourceConstants.NT_SLING_FOLDER, JcrResourceConstants.NT_SLING_FOLDER, true);

        def fileName = scriptData.fileName

        if (folderResource.getChild(fileName)) {
            resourceResolver.delete(folderResource.getChild(fileName))
            resourceResolver.commit();
        }

        saveFile(resourceResolver, folderResource, scriptData.script, fileName, new Date(), MediaType.OCTET_STREAM.toString())

        new DefaultSaveScriptResponse(fileName)
    }

    @Override
    List<ActiveJob> getActiveJobs() {
        jobManager.findJobs(JobManager.QueryType.ACTIVE, GroovyConsoleConstants.JOB_TOPIC, 0, null).collect { job ->
            new ActiveJob(job)
        }
    }

    @Override
    boolean addScheduledJob(JobProperties jobProperties) {
        if (jobProperties.cronExpression) {
            LOG.info("adding scheduled job with properties : {}", jobProperties.toMap())

            jobManager.createJob(GroovyConsoleConstants.JOB_TOPIC)
                    .properties(jobProperties.toMap())
                    .schedule()
                    .cron(jobProperties.cronExpression)
                    .add()
        } else {
            LOG.info("adding immediate job with properties : {}", jobProperties.toMap())

            jobManager.addJob(GroovyConsoleConstants.JOB_TOPIC, jobProperties.toMap())
        }
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    @Synchronized
    void bindNotificationService(NotificationService notificationService) {
        notificationServices.add(notificationService)

        LOG.info("added notification service : {}", notificationService.class.name)
    }

    @Synchronized
    void unbindNotificationService(NotificationService notificationService) {
        notificationServices.remove(notificationService)

        LOG.info("removed notification service : {}", notificationService.class.name)
    }

    // internals

    private void auditAndNotify(RunScriptResponse response) {
        if (!configurationService.auditDisabled) {
            auditService.createAuditRecord(response)
        }

        notificationServices.each { notificationService ->
            notificationService.notify(response)
        }
    }

    private Binding getBinding(ScriptContext scriptContext) {
        def binding = new Binding()

        extensionService.getBindingVariables(scriptContext).each { name, variable ->
            binding.setVariable(name, variable.value)
        }

        binding
    }

    private CompilerConfiguration getConfiguration(LibraryCompilationCustomizer libraryCompilerCustomizer) {
        def configuration = new CompilerConfiguration()

        configuration.addCompilationCustomizers(libraryCompilerCustomizer)

        if (configurationService.threadTimeout > 0) {
            // add timed interrupt using configured timeout value
            configuration.addCompilationCustomizers(new ASTTransformationCustomizer(value: configurationService.threadTimeout, TimedInterrupt))
        }

        configuration.addCompilationCustomizers(extensionService.compilationCustomizers
                as CompilationCustomizer[])
    }

    private void loadLibraries(GroovyShell shell, List<String> libraries) {
        String test = "true";
        return;
    }


    private void saveFile(ResourceResolver resourceResolver, Resource folderResource, String script, String fileName, Date date,
                          String mimeType) {

        def fileResource = resourceResolver.create(folderResource, Text.escapeIllegalJcrChars(fileName),
                [(JcrConstants.JCR_PRIMARYTYPE): JcrConstants.NT_FILE] as Map)

        def fileContentResource = resourceResolver.create(fileResource, JcrConstants.JCR_CONTENT,
                [(JcrConstants.JCR_PRIMARYTYPE): JcrConstants.NT_RESOURCE] as Map)

        def stream = new ByteArrayInputStream(script.getBytes(CHARSET))
        def valueMap = fileContentResource.adaptTo(ModifiableValueMap.class)

        valueMap.put(JcrConstants.JCR_MIMETYPE, mimeType)
        valueMap.put(JcrConstants.JCR_ENCODING, CHARSET)
        valueMap.put(JcrConstants.JCR_DATA, stream)
        valueMap.put(JcrConstants.JCR_LASTMODIFIED, date.time)
        valueMap.put("jcr:lastModifiedBy", resourceResolver.getUserID())

        resourceResolver.commit()
    }
}
