package be.orbinson.aem.groovy.console.migration.servlets

import be.orbinson.aem.groovy.console.migration.MigrationRunOptions
import be.orbinson.aem.groovy.console.migration.MigrationService
import be.orbinson.aem.groovy.console.migration.MigrationStatus
import be.orbinson.aem.groovy.console.migration.impl.DefaultMigrationRun
import be.orbinson.aem.groovy.console.migration.impl.DefaultMigrationScriptResult
import be.orbinson.aem.groovy.console.migration.impl.DefaultMigrationScriptState
import groovy.json.JsonSlurper
import io.wcm.testing.mock.aem.junit5.AemContext
import io.wcm.testing.mock.aem.junit5.AemContextExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor

import static be.orbinson.aem.groovy.console.migration.MigrationConstants.*
import static javax.servlet.http.HttpServletResponse.*
import static org.junit.jupiter.api.Assertions.*
import static org.mockito.ArgumentMatchers.any
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.verify
import static org.mockito.Mockito.when

@ExtendWith(AemContextExtension.class)
class MigrationServletTest {

    private final AemContext context = new AemContext()

    MigrationServlet servlet

    MigrationService migrationService

    @BeforeEach
    void beforeEach() {
        migrationService = mock(MigrationService)

        context.registerService(MigrationService, migrationService)

        servlet = context.registerInjectActivateService(new MigrationServlet())
    }

    @Test
    void postWithoutPermissionReturnsForbidden() {
        when(migrationService.hasPermission(any())).thenReturn(false)

        servlet.doPost(context.request(), context.response())

        assertEquals(SC_FORBIDDEN, context.response().status)
    }

    @Test
    void getWithoutPermissionReturnsForbidden() {
        when(migrationService.hasPermission(any())).thenReturn(false)

        servlet.doGet(context.request(), context.response())

        assertEquals(SC_FORBIDDEN, context.response().status)
    }

    @Test
    void syncPostReturnsAggregateRunResult() {
        when(migrationService.hasPermission(any())).thenReturn(true)
        when(migrationService.run(any(MigrationRunOptions))).thenReturn(migrationRun())

        servlet.doPost(context.request(), context.response())

        assertEquals(SC_OK, context.response().status)

        def json = new JsonSlurper().parseText(context.response().outputAsString)

        assertEquals("test-run-id", json.runId)
        assertEquals("FAILED", json.status)
        assertEquals(1, json.executed)
        assertEquals(1, json.failed)
        assertEquals(1, json.skipped)
        assertEquals("", json.path)
        assertEquals(3, json.results.size())
        assertEquals("SUCCESS", json.results[0].status)
    }

    @Test
    void postWithPathAndDataPassesThemToRunOptions() {
        when(migrationService.hasPermission(any())).thenReturn(true)
        when(migrationService.run(any(MigrationRunOptions))).thenReturn(migrationRun())

        context.request().parameterMap = [
                (PATH): "/conf/groovyconsole/scripts/migration/sub",
                (DATA): '{"foo":"bar"}'
        ]

        servlet.doPost(context.request(), context.response())

        def optionsCaptor = ArgumentCaptor.forClass(MigrationRunOptions)
        verify(migrationService).run(optionsCaptor.capture())

        assertEquals("/conf/groovyconsole/scripts/migration/sub", optionsCaptor.value.path)
        assertEquals('{"foo":"bar"}', optionsCaptor.value.data)
    }

    @Test
    void asyncPostReturnsAcceptedWithRunId() {
        when(migrationService.hasPermission(any())).thenReturn(true)
        when(migrationService.enqueue(any(MigrationRunOptions))).thenReturn("async-run-id")

        context.request().parameterMap = [(ASYNC): "true"]

        servlet.doPost(context.request(), context.response())

        assertEquals(SC_ACCEPTED, context.response().status)

        def json = new JsonSlurper().parseText(context.response().outputAsString)

        assertEquals("async-run-id", json.runId)
        assertEquals("RUNNING", json.status)
    }

    @Test
    void postWhileRunningReturnsConflict() {
        when(migrationService.hasPermission(any())).thenReturn(true)
        when(migrationService.run(any(MigrationRunOptions)))
                .thenThrow(new IllegalStateException("migration run already in progress"))

        servlet.doPost(context.request(), context.response())

        assertEquals(SC_CONFLICT, context.response().status)

        def json = new JsonSlurper().parseText(context.response().outputAsString)

        assertEquals("migration run already in progress", json.error)
    }

    @Test
    void postWithUnexpectedErrorReturnsServerError() {
        when(migrationService.hasPermission(any())).thenReturn(true)
        when(migrationService.run(any(MigrationRunOptions))).thenThrow(new RuntimeException("job queue unavailable"))

        servlet.doPost(context.request(), context.response())

        assertEquals(SC_INTERNAL_SERVER_ERROR, context.response().status)

        def json = new JsonSlurper().parseText(context.response().outputAsString)

        assertEquals("job queue unavailable", json.error)
    }

    @Test
    void getRunByIdReturnsRunDetail() {
        when(migrationService.hasPermission(any())).thenReturn(true)
        when(migrationService.getRun("test-run-id")).thenReturn(migrationRun())

        context.request().parameterMap = [(RUN_ID): "test-run-id"]

        servlet.doGet(context.request(), context.response())

        def json = new JsonSlurper().parseText(context.response().outputAsString)

        assertEquals("test-run-id", json.runId)
        assertEquals(3, json.results.size())
    }

    @Test
    void getRunByUnknownIdReturnsNotFound() {
        when(migrationService.hasPermission(any())).thenReturn(true)
        when(migrationService.getRun("unknown")).thenReturn(null)

        context.request().parameterMap = [(RUN_ID): "unknown"]

        servlet.doGet(context.request(), context.response())

        assertEquals(SC_NOT_FOUND, context.response().status)
    }

    @Test
    void getRegistryReturnsScriptStates() {
        when(migrationService.hasPermission(any())).thenReturn(true)
        when(migrationService.getRegistry()).thenReturn([
                new DefaultMigrationScriptState(
                        scriptPath: "/conf/groovyconsole/scripts/migration/001-first.groovy",
                        checksum: "abc",
                        status: MigrationStatus.SUCCESS,
                        lastRunDate: Calendar.instance,
                        runningTime: "00:00:00.001",
                        always: false,
                        pending: false
                )
        ])

        context.request().parameterMap = [(REGISTRY): "true"]

        servlet.doGet(context.request(), context.response())

        def json = new JsonSlurper().parseText(context.response().outputAsString)

        assertEquals(1, json.data.size())
        assertEquals("SUCCESS", json.data[0].status)
        assertFalse(json.data[0].pending)
    }

    @Test
    void getPendingReturnsPendingScriptPaths() {
        when(migrationService.hasPermission(any())).thenReturn(true)
        when(migrationService.getPendingScripts()).thenReturn(["/conf/groovyconsole/scripts/migration/001-first.groovy"])

        context.request().parameterMap = [(PENDING): "true"]

        servlet.doGet(context.request(), context.response())

        def json = new JsonSlurper().parseText(context.response().outputAsString)

        assertEquals(["/conf/groovyconsole/scripts/migration/001-first.groovy"], json.data)
    }

    @Test
    void getDefaultReturnsRunSummaries() {
        when(migrationService.hasPermission(any())).thenReturn(true)
        when(migrationService.isRunning()).thenReturn(false)
        when(migrationService.getRuns()).thenReturn([migrationRun()])

        servlet.doGet(context.request(), context.response())

        def json = new JsonSlurper().parseText(context.response().outputAsString)

        assertFalse(json.running)
        assertEquals(1, json.data.size())
        assertEquals("test-run-id", json.data[0].runId)
        assertNull(json.data[0].results)
    }

    private static DefaultMigrationRun migrationRun() {
        new DefaultMigrationRun(
                runId: "test-run-id",
                status: MigrationStatus.FAILED,
                trigger: TRIGGER_API,
                startDate: Calendar.instance,
                endDate: Calendar.instance,
                runningTime: "00:00:01.234",
                results: [
                        scriptResult("001-first.groovy", MigrationStatus.SUCCESS),
                        scriptResult("002-second.groovy", MigrationStatus.FAILED),
                        scriptResult("003-third.groovy", MigrationStatus.SKIPPED)
                ]
        )
    }

    private static DefaultMigrationScriptResult scriptResult(String name, MigrationStatus status) {
        new DefaultMigrationScriptResult(
                scriptPath: "/conf/groovyconsole/scripts/migration/$name",
                checksum: "abc",
                status: status,
                runningTime: "00:00:00.123",
                durationMillis: 123,
                output: "output",
                error: status == MigrationStatus.FAILED ? "error" : ""
        )
    }
}
