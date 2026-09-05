package be.orbinson.aem.groovy.console.migration.impl

import be.orbinson.aem.groovy.console.GroovyConsoleService
import be.orbinson.aem.groovy.console.migration.MigrationRunOptions
import be.orbinson.aem.groovy.console.migration.MigrationService
import be.orbinson.aem.groovy.console.migration.MigrationStatus
import be.orbinson.aem.groovy.console.response.impl.DefaultRunScriptResponse
import io.wcm.testing.mock.aem.junit5.AemContext
import io.wcm.testing.mock.aem.junit5.AemContextExtension
import org.apache.sling.event.jobs.Job
import org.apache.sling.event.jobs.JobManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

import java.nio.charset.StandardCharsets

import static be.orbinson.aem.groovy.console.migration.MigrationConstants.*
import static org.junit.jupiter.api.Assertions.*
import static org.mockito.ArgumentMatchers.any
import static org.mockito.ArgumentMatchers.anyLong
import static org.mockito.ArgumentMatchers.anyString
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.when

@ExtendWith(AemContextExtension.class)
class DefaultMigrationServiceTest {

    private static final String FAIL_MARKER = "FAIL"

    private static final String DIRTY_MARKER = "DIRTY"

    private static final String DIRTY_RESOURCE_PATH = "/migration-dirty-content"

    private final AemContext context = new AemContext()

    MigrationService migrationService

    JobManager jobManager

    List<String> executedScripts = []

    List<String> executedData = []

    @BeforeEach
    void beforeEach() {
        context.build().resource(DEFAULT_SCRIPTS_BASE_PATH).commit()

        // stub console service that fails scripts containing the fail marker and records executions;
        // scripts containing the dirty marker leave uncommitted changes in the resolver
        def consoleService = [
                runScript      : { scriptContext ->
                    executedScripts << scriptContext.script
                    executedData << scriptContext.data

                    if (scriptContext.script.contains(DIRTY_MARKER)) {
                        def resolver = scriptContext.resourceResolver

                        resolver.create(resolver.getResource("/"), DIRTY_RESOURCE_PATH.substring(1), [:])
                    }

                    if (scriptContext.script.contains(FAIL_MARKER)) {
                        DefaultRunScriptResponse.fromException(scriptContext, "output",
                                new RuntimeException("script failed"))
                    } else {
                        DefaultRunScriptResponse.fromResult(scriptContext, "result",
                                "output for ${scriptContext.script}" as String, "00:00:00.001")
                    }
                },
                saveScript     : { scriptData -> null },
                addScheduledJob: { jobProperties -> false },
                getActiveJobs  : { [] }
        ] as GroovyConsoleService

        context.registerService(GroovyConsoleService, consoleService)

        jobManager = mock(JobManager)
        context.registerService(JobManager, jobManager)

        migrationService = context.registerInjectActivateService(new DefaultMigrationService())
    }

    @Test
    void runExecutesNewScriptsInPathOrder() {
        addScript("002-second.groovy", "script two")
        addScript("001-first.groovy", "script one")

        def run = migrationService.run(new MigrationRunOptions())

        assertEquals(MigrationStatus.SUCCESS, run.status)
        assertEquals(["script one", "script two"], executedScripts)
        assertEquals(2, run.results.size())
        assertEquals(MigrationStatus.SUCCESS, run.results[0].status)
        assertNotNull(run.runId)
        assertNotNull(migrationService.getRun(run.runId))
    }

    @Test
    void unchangedScriptIsNotExecutedTwice() {
        addScript("001-first.groovy", "script one")

        migrationService.run(new MigrationRunOptions())
        def secondRun = migrationService.run(new MigrationRunOptions())

        assertEquals(["script one"], executedScripts)
        assertEquals(MigrationStatus.SUCCESS, secondRun.status)
        assertTrue(secondRun.results.empty)
    }

    @Test
    void changedScriptIsExecutedAgain() {
        addScript("001-first.groovy", "script one")

        migrationService.run(new MigrationRunOptions())

        replaceScript("001-first.groovy", "script one changed")

        migrationService.run(new MigrationRunOptions())

        assertEquals(["script one", "script one changed"], executedScripts)
    }

    @Test
    void alwaysScriptIsExecutedOnEveryRun() {
        addScript("001-first.always.groovy", "script always")

        migrationService.run(new MigrationRunOptions())
        migrationService.run(new MigrationRunOptions())

        assertEquals(["script always", "script always"], executedScripts)
    }

    @Test
    void failFastSkipsRemainingScriptsAndRetriesOnNextRun() {
        addScript("001-first.groovy", "script one")
        addScript("002-second.groovy", "script two $FAIL_MARKER")
        addScript("003-third.groovy", "script three")

        def run = migrationService.run(new MigrationRunOptions())

        assertEquals(MigrationStatus.FAILED, run.status)
        assertEquals(3, run.results.size())
        assertEquals(MigrationStatus.SUCCESS, run.results[0].status)
        assertEquals(MigrationStatus.FAILED, run.results[1].status)
        assertEquals(MigrationStatus.SKIPPED, run.results[2].status)
        assertEquals(["script one", "script two $FAIL_MARKER" as String], executedScripts)

        // failed and skipped scripts are pending again, the successful one is not
        def pendingScripts = migrationService.pendingScripts

        assertEquals([
                "$DEFAULT_SCRIPTS_BASE_PATH/002-second.groovy" as String,
                "$DEFAULT_SCRIPTS_BASE_PATH/003-third.groovy" as String
        ], pendingScripts)
    }

    @Test
    void dryRunReportsPendingScriptsWithoutExecuting() {
        addScript("001-first.groovy", "script one")

        def run = migrationService.run(new MigrationRunOptions(dryRun: true))

        assertEquals(MigrationStatus.SUCCESS, run.status)
        assertEquals(1, run.results.size())
        assertEquals(MigrationStatus.PENDING, run.results[0].status)
        assertTrue(executedScripts.empty)
        assertEquals(["$DEFAULT_SCRIPTS_BASE_PATH/001-first.groovy" as String], migrationService.pendingScripts)
    }

    @Test
    void runWithPathScopesToFolder() {
        addScript("001-first.groovy", "script one")
        addScript("sub/001-nested.groovy", "script nested")
        addScript("sub/002-nested.groovy", "script nested two")

        def run = migrationService.run(new MigrationRunOptions(path: "$DEFAULT_SCRIPTS_BASE_PATH/sub"))

        assertEquals(MigrationStatus.SUCCESS, run.status)
        assertEquals(["script nested", "script nested two"], executedScripts)
        assertEquals("$DEFAULT_SCRIPTS_BASE_PATH/sub" as String, run.path)

        // the script outside the scoped path is untouched and still pending
        assertEquals(["$DEFAULT_SCRIPTS_BASE_PATH/001-first.groovy" as String], migrationService.pendingScripts)
    }

    @Test
    void runWithPathScopesToSingleScript() {
        addScript("001-first.groovy", "script one")
        addScript("002-second.groovy", "script two")

        migrationService.run(new MigrationRunOptions(path: "$DEFAULT_SCRIPTS_BASE_PATH/002-second.groovy"))

        assertEquals(["script two"], executedScripts)
    }

    @Test
    void runWithoutPathDefaultsToEmptyPath() {
        addScript("001-first.groovy", "script one")

        def run = migrationService.run(new MigrationRunOptions())

        assertEquals("", run.path)
    }

    @Test
    void runPassesDataToScriptContext() {
        addScript("001-first.groovy", "script one")

        migrationService.run(new MigrationRunOptions(data: '{"foo":"bar"}'))

        assertEquals(['{"foo":"bar"}'], executedData)
    }

    @Test
    void runWithoutDataPassesNullToScriptContext() {
        addScript("001-first.groovy", "script one")

        migrationService.run(new MigrationRunOptions())

        assertEquals([null], executedData)
    }

    @Test
    void runModeTokenFilteredWhenRunModeNotActive() {
        // mock bundle context has no run modes, so author-only scripts are filtered
        addScript("001-author-only.author.groovy", "script author")
        addScript("002-everywhere.groovy", "script everywhere")

        migrationService.run(new MigrationRunOptions())

        assertEquals(["script everywhere"], executedScripts)
    }

    @Test
    void runModeTokenMatchingActiveRunModeIsExecuted() {
        context.runMode("author")

        // re-register so the service picks up the run modes
        migrationService = context.registerInjectActivateService(new DefaultMigrationService())

        addScript("001-author-only.author.groovy", "script author")
        addScript("002-publish-only.publish.groovy", "script publish")

        migrationService.run(new MigrationRunOptions())

        assertEquals(["script author"], executedScripts)
    }

    @Test
    void runModeTokenIgnoredWhenFilterDisabled() {
        migrationService = context.registerInjectActivateService(new DefaultMigrationService(),
                [runModeFilterEnabled: false])

        addScript("001-author-only.author.groovy", "script author")

        migrationService.run(new MigrationRunOptions())

        assertEquals(["script author"], executedScripts)
    }

    @Test
    void runThrowsWhenMigrationAlreadyInProgress() {
        context.build().resource(PATH_MIGRATION_ROOT, [
                (PN_RUNNING)       : true,
                (PN_RUN_STARTED_AT): Calendar.instance
        ]).commit()

        assertTrue(migrationService.running)
        assertThrows(IllegalStateException, { migrationService.run(new MigrationRunOptions()) })
    }

    @Test
    void staleLockIsTakenOver() {
        def staleStartedAt = Calendar.instance
        staleStartedAt.add(Calendar.HOUR, -2)

        context.build().resource(PATH_MIGRATION_ROOT, [
                (PN_RUNNING)       : true,
                (PN_RUN_STARTED_AT): staleStartedAt
        ]).commit()

        addScript("001-first.groovy", "script one")

        assertFalse(migrationService.running)

        def run = migrationService.run(new MigrationRunOptions())

        assertEquals(MigrationStatus.SUCCESS, run.status)
        assertEquals(["script one"], executedScripts)
    }

    @Test
    void runHistoryIsPrunedToMaximum() {
        migrationService = context.registerInjectActivateService(new DefaultMigrationService(),
                [maxRunHistory: 2])

        addScript("001-first.always.groovy", "script always")

        (1..4).each {
            migrationService.run(new MigrationRunOptions())
        }

        assertEquals(2, migrationService.runs.size())
    }

    @Test
    void outputIsTruncatedToMaximum() {
        migrationService = context.registerInjectActivateService(new DefaultMigrationService(),
                [maxOutputChars: 10])

        addScript("001-first.groovy", "script one")

        def run = migrationService.run(new MigrationRunOptions())

        assertEquals(10, run.results[0].output.length())
    }

    @Test
    void registryReflectsScriptStates() {
        addScript("001-first.groovy", "script one")
        addScript("002-second.always.groovy", "script always")

        def newStates = migrationService.registry

        assertEquals(2, newStates.size())
        assertTrue(newStates.every { state -> state.pending })
        assertNull(newStates[0].status)

        migrationService.run(new MigrationRunOptions())

        def states = migrationService.registry

        assertEquals(MigrationStatus.SUCCESS, states[0].status)
        assertFalse(states[0].pending)
        assertFalse(states[0].always)

        // always scripts stay pending
        assertTrue(states[1].pending)
        assertTrue(states[1].always)
    }

    @Test
    void failedScriptChangesAreReverted() {
        addScript("001-fail.groovy", "script $DIRTY_MARKER $FAIL_MARKER")

        def run = migrationService.run(new MigrationRunOptions())

        assertEquals(MigrationStatus.FAILED, run.status)
        assertNull(context.resourceResolver().getResource(DIRTY_RESOURCE_PATH))
    }

    @Test
    void successfulScriptChangesArePersisted() {
        addScript("001-dirty.groovy", "script $DIRTY_MARKER")

        def run = migrationService.run(new MigrationRunOptions())

        assertEquals(MigrationStatus.SUCCESS, run.status)
        assertNotNull(context.resourceResolver().getResource(DIRTY_RESOURCE_PATH))
    }

    @Test
    void enqueueThrowsWhenJobAlreadyQueued() {
        when(jobManager.findJobs(any(), anyString(), anyLong(), any())).thenReturn([mock(Job)])

        assertThrows(IllegalStateException, { migrationService.enqueue(new MigrationRunOptions()) })
    }

    @Test
    void enqueueMarksRunFailedWhenJobCannotBeAdded() {
        // jobManager.addJob is not stubbed and returns null
        assertThrows(MigrationJobException, { migrationService.enqueue(new MigrationRunOptions()) })

        def runs = migrationService.runs

        assertEquals(1, runs.size())
        assertEquals(MigrationStatus.FAILED, runs[0].status)
        assertFalse(runs[0].error.empty)
    }

    @Test
    void enqueueCreatesRunningRunAndAddsJob() {
        when(jobManager.addJob(anyString(), any(Map))).thenReturn(mock(Job))

        def runId = migrationService.enqueue(new MigrationRunOptions())

        assertNotNull(runId)

        def run = migrationService.getRun(runId)

        assertEquals(MigrationStatus.RUNNING, run.status)
        assertEquals(TRIGGER_API, run.trigger)
    }

    @Test
    void getRunReturnsNullForUnknownRunId() {
        assertNull(migrationService.getRun("unknown"))
        assertNull(migrationService.getRun(null))
    }

    @Test
    void discoversScriptsFromBothDefaultBasePathsInPathOrder() {
        context.build().resource(DEFAULT_SCRIPTS_BASE_PATH_APPS).commit()

        addScriptAt(DEFAULT_SCRIPTS_BASE_PATH_APPS, "001-apps.groovy", "apps script")
        addScript("001-conf.groovy", "conf script")

        def run = migrationService.run(new MigrationRunOptions())

        assertEquals(MigrationStatus.SUCCESS, run.status)
        // /apps sorts before /conf, so the immutable script runs first
        assertEquals(["apps script", "conf script"], executedScripts)
        assertEquals([
                "$DEFAULT_SCRIPTS_BASE_PATH_APPS/001-apps.groovy" as String,
                "$DEFAULT_SCRIPTS_BASE_PATH/001-conf.groovy" as String
        ], run.results*.scriptPath)
    }

    @Test
    void honorsConfiguredSingleBasePath() {
        migrationService = context.registerInjectActivateService(new DefaultMigrationService(),
                [scriptsBasePaths: [DEFAULT_SCRIPTS_BASE_PATH_APPS] as String[]])

        context.build().resource(DEFAULT_SCRIPTS_BASE_PATH_APPS).commit()

        addScriptAt(DEFAULT_SCRIPTS_BASE_PATH_APPS, "001-apps.groovy", "apps script")
        addScript("001-conf.groovy", "conf script")

        migrationService.run(new MigrationRunOptions())

        // only the /apps path is configured, so the /conf script is ignored
        assertEquals(["apps script"], executedScripts)
    }

    private void addScript(String name, String content) {
        addScriptAt(DEFAULT_SCRIPTS_BASE_PATH, name, content)
    }

    private void addScriptAt(String basePath, String name, String content) {
        context.load().binaryFile(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
                "$basePath/$name")
    }

    private void replaceScript(String name, String content) {
        def resourceResolver = context.resourceResolver()
        def resource = resourceResolver.getResource("$DEFAULT_SCRIPTS_BASE_PATH/$name")

        if (resource) {
            resourceResolver.delete(resource)
            resourceResolver.commit()
        }

        addScript(name, content)
    }
}
