package be.orbinson.aem.groovy.console.migration.impl

import be.orbinson.aem.groovy.console.GroovyConsoleService
import be.orbinson.aem.groovy.console.api.context.impl.ResourceScriptContext
import be.orbinson.aem.groovy.console.migration.MigrationRun
import be.orbinson.aem.groovy.console.migration.MigrationRunOptions
import be.orbinson.aem.groovy.console.migration.MigrationScriptResult
import be.orbinson.aem.groovy.console.migration.MigrationScriptState
import be.orbinson.aem.groovy.console.migration.MigrationService
import be.orbinson.aem.groovy.console.migration.MigrationStatus
import groovy.transform.Synchronized
import groovy.util.logging.Slf4j
import org.apache.jackrabbit.JcrConstants
import org.apache.jackrabbit.api.security.user.User
import org.apache.jackrabbit.api.security.user.UserManager
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.resource.ModifiableValueMap
import org.apache.sling.api.resource.Resource
import org.apache.sling.api.resource.ResourceResolver
import org.apache.sling.api.resource.ResourceResolverFactory
import org.apache.sling.api.resource.ResourceUtil
import org.apache.sling.api.resource.ValueMap
import org.apache.sling.event.jobs.JobManager
import org.apache.sling.jcr.resource.api.JcrResourceConstants
import org.apache.sling.settings.SlingSettingsService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.metatype.annotations.Designate

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import static be.orbinson.aem.groovy.console.constants.GroovyConsoleConstants.CHARSET
import static be.orbinson.aem.groovy.console.constants.GroovyConsoleConstants.EXTENSION_GROOVY
import static be.orbinson.aem.groovy.console.constants.GroovyConsoleConstants.FORMAT_RUNNING_TIME
import static be.orbinson.aem.groovy.console.constants.GroovyConsoleConstants.TIME_ZONE_RUNNING_TIME
import static be.orbinson.aem.groovy.console.migration.MigrationConstants.*

@Component(service = MigrationService, immediate = true)
@Designate(ocd = MigrationServiceProperties)
@Slf4j("LOG")
class DefaultMigrationService implements MigrationService {

    private static final String TOKEN_ALWAYS = "always"

    private static final Set<String> RUN_MODE_TOKENS = ["author", "publish"] as Set

    private static final Integer REGISTRY_NODE_NAME_LENGTH = 32

    @Reference
    private ResourceResolverFactory resourceResolverFactory

    @Reference
    private GroovyConsoleService groovyConsoleService

    @Reference
    private JobManager jobManager

    @Reference
    private SlingSettingsService slingSettingsService

    private String scriptsBasePath

    private Set<String> allowedMigrationGroups

    private long staleLockMillis

    private int maxRunHistory

    private int maxOutputChars

    private boolean runModeFilterEnabled

    @Activate
    @Synchronized
    void activate(MigrationServiceProperties properties) {
        scriptsBasePath = properties.scriptsBasePath()
        allowedMigrationGroups = (properties.allowedMigrationGroups() ?: []).findAll() as Set
        staleLockMillis = properties.staleLockMillis()
        maxRunHistory = properties.maxRunHistory()
        maxOutputChars = properties.maxOutputChars()
        runModeFilterEnabled = properties.runModeFilterEnabled()
    }

    @Override
    MigrationRun run(MigrationRunOptions options) {
        withResourceResolver { ResourceResolver resourceResolver ->
            acquireLock(resourceResolver)

            def runId = options.runId ?: UUID.randomUUID().toString()
            def startDate = Calendar.instance

            try {
                def pendingScripts = findScripts(resourceResolver).findAll { script ->
                    isPendingScript(resourceResolver, script)
                }

                LOG.info("found {} pending migration script(s) below path : {}", pendingScripts.size(), scriptsBasePath)

                def results
                def status

                if (options.dryRun) {
                    results = pendingScripts.collect { script -> dryRunResult(script) }
                    status = MigrationStatus.SUCCESS
                } else {
                    results = executeScripts(resourceResolver, pendingScripts)
                    status = results.any { result -> result.status == MigrationStatus.FAILED } ?
                            MigrationStatus.FAILED : MigrationStatus.SUCCESS
                }

                def endDate = Calendar.instance

                def run = new DefaultMigrationRun(
                        runId: runId,
                        status: status,
                        trigger: options.trigger,
                        startDate: startDate,
                        endDate: endDate,
                        runningTime: formatRunningTime(endDate.timeInMillis - startDate.timeInMillis),
                        results: results
                )

                persistRun(resourceResolver, run)
                pruneRuns(resourceResolver)

                LOG.info("finished migration run : {}", run)

                run
            } finally {
                releaseLock(resourceResolver)
            }
        }
    }

    @Override
    String enqueue(MigrationRunOptions options) {
        if (running || hasQueuedJobs()) {
            throw new IllegalStateException("migration run already in progress")
        }

        def runId = options.runId ?: UUID.randomUUID().toString()

        withResourceResolver { ResourceResolver resourceResolver ->
            def runResource = getOrCreateResource(resourceResolver, "$PATH_MIGRATION_RUNS/$runId")
            def valueMap = runResource.adaptTo(ModifiableValueMap)

            valueMap.put(PN_STATUS, MigrationStatus.RUNNING.name())
            valueMap.put(PN_TRIGGER, options.trigger)
            valueMap.put(PN_START_DATE, Calendar.instance)

            resourceResolver.commit()
        }

        def job = jobManager.addJob(MIGRATION_JOB_TOPIC, [
                (RUN_ID)    : runId,
                (PN_TRIGGER): options.trigger,
                (DRY_RUN)   : options.dryRun
        ] as Map<String, Object>)

        if (!job) {
            markRunFailed(runId, "unable to add migration job")

            throw new MigrationJobException("unable to add migration job for run ID : $runId")
        }

        LOG.info("enqueued migration job with run ID : {}", runId)

        runId
    }

    private boolean hasQueuedJobs() {
        !(jobManager.findJobs(JobManager.QueryType.ALL, MIGRATION_JOB_TOPIC, 1, (Map<String, Object>[]) null) ?: []).empty
    }

    private void markRunFailed(String runId, String error) {
        withResourceResolver { ResourceResolver resourceResolver ->
            def runResource = resourceResolver.getResource("$PATH_MIGRATION_RUNS/$runId")

            if (runResource) {
                def valueMap = runResource.adaptTo(ModifiableValueMap)

                valueMap.put(PN_STATUS, MigrationStatus.FAILED.name())
                valueMap.put(PN_ERROR, error)
                valueMap.put(PN_END_DATE, Calendar.instance)

                resourceResolver.commit()
            }
        }
    }

    @Override
    MigrationRun getRun(String runId) {
        withResourceResolver { ResourceResolver resourceResolver ->
            def runResource = runId ? resourceResolver.getResource("$PATH_MIGRATION_RUNS/$runId") : null

            runResource ? DefaultMigrationRun.fromResource(runResource) : null
        }
    }

    @Override
    List<MigrationRun> getRuns() {
        withResourceResolver { ResourceResolver resourceResolver ->
            def runsResource = resourceResolver.getResource(PATH_MIGRATION_RUNS)

            (runsResource?.listChildren() ?: [])
                    .collect { resource -> DefaultMigrationRun.fromResource(resource) }
                    .sort { run -> -(run.startDate?.timeInMillis ?: 0) }
        }
    }

    @Override
    List<MigrationScriptState> getRegistry() {
        withResourceResolver { ResourceResolver resourceResolver ->
            findScripts(resourceResolver).collect { script ->
                def properties = getRegistryEntry(resourceResolver, script.path)?.valueMap
                def status = properties?.get(PN_STATUS, String)

                new DefaultMigrationScriptState(
                        scriptPath: script.path,
                        checksum: properties?.get(PN_CHECKSUM, String) ?: script.checksum,
                        status: status ? MigrationStatus.valueOf(status) : null,
                        lastRunDate: properties?.get(PN_LAST_RUN_DATE, Calendar),
                        runningTime: properties?.get(PN_RUNNING_TIME, String) ?: "",
                        always: script.always,
                        pending: isPending(script, properties)
                )
            }
        }
    }

    @Override
    List<String> getPendingScripts() {
        withResourceResolver { ResourceResolver resourceResolver ->
            findScripts(resourceResolver)
                    .findAll { script -> isPendingScript(resourceResolver, script) }
                    .collect { script -> script.path }
        }
    }

    @Override
    boolean isRunning() {
        withResourceResolver { ResourceResolver resourceResolver ->
            isLocked(resourceResolver.getResource(PATH_MIGRATION_ROOT))
        }
    }

    @Override
    boolean hasPermission(SlingHttpServletRequest request) {
        withResourceResolver { ResourceResolver resourceResolver ->
            def userManager = resourceResolver.adaptTo(UserManager)

            if (userManager != null) {
                def principal = request.userPrincipal
                def authorizable = principal ? userManager.getAuthorizable(principal) : null

                if (authorizable instanceof User) {
                    def user = authorizable as User
                    def memberOfGroupIds = user.memberOf()*.ID

                    LOG.debug("member of group IDs : {}, allowed migration group IDs : {}", memberOfGroupIds,
                            allowedMigrationGroups)

                    user.admin || (allowedMigrationGroups ?
                            memberOfGroupIds.intersect(allowedMigrationGroups as Iterable) : false)
                } else {
                    LOG.debug("no user found for request principal : {}", principal)

                    false
                }
            } else {
                LOG.debug("UserManager not available, probably in a Sling based application, falling back to is admin check")

                request.resourceResolver.userID == "admin"
            }
        }
    }

    // script discovery

    private List<Map> findScripts(ResourceResolver resourceResolver) {
        def scripts = []

        def baseResource = resourceResolver.getResource(scriptsBasePath)

        if (baseResource) {
            collectScripts(baseResource, scripts)
        } else {
            LOG.debug("migration scripts base path not found : {}", scriptsBasePath)
        }

        scripts.sort { script -> script.path }
    }

    private void collectScripts(Resource resource, List<Map> scripts) {
        resource.listChildren().each { child ->
            if (child.name.endsWith(EXTENSION_GROOVY)) {
                if (matchesRunModes(child.name)) {
                    def content = loadScript(child)

                    if (content) {
                        scripts << [
                                path    : child.path,
                                content : content,
                                checksum: checksum(content),
                                always  : specialTokens(child.name).contains(TOKEN_ALWAYS)
                        ]
                    } else {
                        LOG.warn("migration script has no content : {}", child.path)
                    }
                } else {
                    LOG.debug("skipping migration script due to run mode filter : {}", child.path)
                }
            } else {
                collectScripts(child, scripts)
            }
        }
    }

    private String loadScript(Resource resource) {
        def stream = resource.adaptTo(InputStream)

        if (stream == null) {
            stream = resource.getChild(JcrConstants.JCR_CONTENT)?.valueMap?.get(JcrConstants.JCR_DATA, InputStream)
        }

        stream?.getText(CHARSET)
    }

    private boolean matchesRunModes(String scriptName) {
        def scriptRunModes = specialTokens(scriptName).findAll { token -> RUN_MODE_TOKENS.contains(token) }

        !runModeFilterEnabled || scriptRunModes.empty ||
                !scriptRunModes.intersect(slingSettingsService.runModes as Iterable).empty
    }

    /**
     * Get the special file name tokens between the script name and the .groovy extension,
     * e.g. "script.author.always.groovy" yields ["author", "always"].
     */
    private static List<String> specialTokens(String scriptName) {
        def parts = (scriptName - EXTENSION_GROOVY).tokenize(".")

        parts.size() > 1 ? parts.tail() : []
    }

    // pending computation

    private boolean isPendingScript(ResourceResolver resourceResolver, Map script) {
        isPending(script, getRegistryEntry(resourceResolver, script.path)?.valueMap)
    }

    private static boolean isPending(Map script, ValueMap properties) {
        script.always ||
                !properties ||
                properties.get(PN_CHECKSUM, "") != script.checksum ||
                properties.get(PN_STATUS, "") != MigrationStatus.SUCCESS.name()
    }

    private Resource getRegistryEntry(ResourceResolver resourceResolver, String scriptPath) {
        resourceResolver.getResource("$PATH_MIGRATION_REGISTRY/${registryNodeName(scriptPath)}")
    }

    private static String registryNodeName(String scriptPath) {
        checksum(scriptPath).take(REGISTRY_NODE_NAME_LENGTH)
    }

    // execution

    private List<MigrationScriptResult> executeScripts(ResourceResolver resourceResolver, List<Map> pendingScripts) {
        def results = []

        def failed = false

        pendingScripts.each { script ->
            def result

            if (failed) {
                LOG.info("skipping migration script due to preceding failure : {}", script.path)

                result = new DefaultMigrationScriptResult(
                        scriptPath: script.path,
                        checksum: script.checksum,
                        status: MigrationStatus.SKIPPED,
                        runningTime: "",
                        durationMillis: 0,
                        output: "",
                        error: ""
                )
            } else {
                result = executeScript(resourceResolver, script)
                failed = result.status == MigrationStatus.FAILED

                if (failed) {
                    // discard uncommitted changes of the failed script so they are not persisted
                    // by the subsequent registry commit
                    resourceResolver.revert()
                }
            }

            updateRegistry(resourceResolver, script, result)

            results << result
        }

        results
    }

    private MigrationScriptResult executeScript(ResourceResolver resourceResolver, Map script) {
        LOG.info("executing migration script : {}", script.path)

        def started = System.currentTimeMillis()

        def outputStream = new ByteArrayOutputStream()

        def scriptContext = new ResourceScriptContext(
                resourceResolver: resourceResolver,
                outputStream: outputStream,
                printStream: new PrintStream(outputStream, true, StandardCharsets.UTF_8.name()),
                script: script.content
        )

        def response = groovyConsoleService.runScript(scriptContext)

        def durationMillis = System.currentTimeMillis() - started

        new DefaultMigrationScriptResult(
                scriptPath: script.path,
                checksum: script.checksum,
                status: response.exceptionStackTrace ? MigrationStatus.FAILED : MigrationStatus.SUCCESS,
                runningTime: formatRunningTime(durationMillis),
                durationMillis: durationMillis,
                output: truncate(response.output),
                error: truncate(response.exceptionStackTrace)
        )
    }

    private MigrationScriptResult dryRunResult(Map script) {
        new DefaultMigrationScriptResult(
                scriptPath: script.path,
                checksum: script.checksum,
                status: MigrationStatus.PENDING,
                runningTime: "",
                durationMillis: 0,
                output: "",
                error: ""
        )
    }

    // persistence

    private void updateRegistry(ResourceResolver resourceResolver, Map script, MigrationScriptResult result) {
        def entryResource = getOrCreateResource(resourceResolver,
                "$PATH_MIGRATION_REGISTRY/${registryNodeName(script.path)}")

        def valueMap = entryResource.adaptTo(ModifiableValueMap)

        valueMap.put(PN_SCRIPT_PATH, script.path)
        valueMap.put(PN_CHECKSUM, script.checksum)
        valueMap.put(PN_STATUS, result.status.name())
        valueMap.put(PN_LAST_RUN_DATE, Calendar.instance)
        valueMap.put(PN_RUNNING_TIME, result.runningTime)

        resourceResolver.commit()
    }

    private void persistRun(ResourceResolver resourceResolver, DefaultMigrationRun run) {
        def runResource = getOrCreateResource(resourceResolver, "$PATH_MIGRATION_RUNS/${run.runId}")

        def valueMap = runResource.adaptTo(ModifiableValueMap)

        valueMap.put(PN_STATUS, run.status.name())
        valueMap.put(PN_TRIGGER, run.trigger)
        valueMap.put(PN_START_DATE, run.startDate)
        valueMap.put(PN_END_DATE, run.endDate)
        valueMap.put(PN_RUNNING_TIME, run.runningTime)
        valueMap.put(PN_ERROR, run.error ?: "")

        run.results.eachWithIndex { result, index ->
            def resultName = "${String.format('%03d', index)}-${result.scriptPath.tokenize('/').last()}"
            def resultResource = getOrCreateResource(resourceResolver,
                    "${runResource.path}/$NN_RESULTS/$resultName")

            def resultValueMap = resultResource.adaptTo(ModifiableValueMap)

            resultValueMap.put(PN_SCRIPT_PATH, result.scriptPath)
            resultValueMap.put(PN_CHECKSUM, result.checksum)
            resultValueMap.put(PN_STATUS, result.status.name())
            resultValueMap.put(PN_RUNNING_TIME, result.runningTime)
            resultValueMap.put(PN_DURATION_MILLIS, result.durationMillis)
            resultValueMap.put(PN_OUTPUT, result.output ?: "")
            resultValueMap.put(PN_ERROR, result.error ?: "")
        }

        resourceResolver.commit()
    }

    private void pruneRuns(ResourceResolver resourceResolver) {
        def runsResource = resourceResolver.getResource(PATH_MIGRATION_RUNS)

        if (runsResource) {
            def runResources = runsResource.listChildren().toList().sort { resource ->
                resource.valueMap.get(PN_START_DATE, Calendar)?.timeInMillis ?: 0
            }

            while (runResources.size() > maxRunHistory) {
                def oldestRunResource = runResources.remove(0)

                LOG.debug("pruning migration run : {}", oldestRunResource.path)

                resourceResolver.delete(oldestRunResource)
            }

            resourceResolver.commit()
        }
    }

    // locking

    @Synchronized
    private void acquireLock(ResourceResolver resourceResolver) {
        def rootResource = getOrCreateResource(resourceResolver, PATH_MIGRATION_ROOT)

        if (isLocked(rootResource)) {
            throw new IllegalStateException("migration run already in progress")
        }

        def valueMap = rootResource.adaptTo(ModifiableValueMap)

        valueMap.put(PN_RUNNING, true)
        valueMap.put(PN_RUN_STARTED_AT, Calendar.instance)

        resourceResolver.commit()
    }

    private void releaseLock(ResourceResolver resourceResolver) {
        def rootResource = resourceResolver.getResource(PATH_MIGRATION_ROOT)

        if (rootResource) {
            rootResource.adaptTo(ModifiableValueMap).put(PN_RUNNING, false)

            resourceResolver.commit()
        }
    }

    private boolean isLocked(Resource rootResource) {
        def locked = false

        if (rootResource?.valueMap?.get(PN_RUNNING, false)) {
            def runStartedAt = rootResource.valueMap.get(PN_RUN_STARTED_AT, Calendar)

            if (runStartedAt == null || System.currentTimeMillis() - runStartedAt.timeInMillis < staleLockMillis) {
                locked = true
            } else {
                LOG.warn("stale migration run lock detected, started at : {}", runStartedAt?.time)
            }
        }

        locked
    }

    // helpers

    private static Resource getOrCreateResource(ResourceResolver resourceResolver, String path) {
        ResourceUtil.getOrCreateResource(resourceResolver, path, JcrConstants.NT_UNSTRUCTURED,
                JcrResourceConstants.NT_SLING_FOLDER, false)
    }

    private static String checksum(String content) {
        MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)).encodeHex().toString()
    }

    private static String formatRunningTime(long durationMillis) {
        new Date(durationMillis).format(FORMAT_RUNNING_TIME, TimeZone.getTimeZone(TIME_ZONE_RUNNING_TIME))
    }

    private String truncate(String value) {
        def truncated = value ?: ""

        truncated.length() > maxOutputChars ? truncated.substring(0, maxOutputChars) : truncated
    }

    private <T> T withResourceResolver(Closure<T> closure) {
        resourceResolverFactory.getServiceResourceResolver(null).withCloseable(closure)
    }
}
