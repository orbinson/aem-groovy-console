package be.orbinson.aem.groovy.console.migration.jmx

import be.orbinson.aem.groovy.console.migration.MigrationRunOptions
import be.orbinson.aem.groovy.console.migration.MigrationService
import be.orbinson.aem.groovy.console.migration.MigrationStatus
import be.orbinson.aem.groovy.console.migration.impl.DefaultMigrationRun
import be.orbinson.aem.groovy.console.migration.impl.DefaultMigrationScriptResult
import io.wcm.testing.mock.aem.junit5.AemContext
import io.wcm.testing.mock.aem.junit5.AemContextExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor

import static be.orbinson.aem.groovy.console.migration.MigrationConstants.TRIGGER_JMX
import static org.junit.jupiter.api.Assertions.*
import static org.mockito.ArgumentMatchers.any
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.verify
import static org.mockito.Mockito.when

@ExtendWith(AemContextExtension.class)
class DefaultMigrationServiceMBeanTest {

    private final AemContext context = new AemContext()

    MigrationServiceMBean bean

    MigrationService migrationService

    @BeforeEach
    void beforeEach() {
        migrationService = mock(MigrationService)

        context.registerService(MigrationService, migrationService)

        bean = context.registerInjectActivateService(new DefaultMigrationServiceMBean())
    }

    @Test
    void isRunningDelegatesToService() {
        when(migrationService.running).thenReturn(true)

        assertTrue(bean.running)
    }

    @Test
    void pendingScriptsDelegatesToService() {
        when(migrationService.pendingScripts).thenReturn(["/conf/groovyconsole/scripts/migration/001-first.groovy"])

        assertEquals(["/conf/groovyconsole/scripts/migration/001-first.groovy"], bean.pendingScripts)
    }

    @Test
    void runWithoutArgumentsUsesJmxTriggerAndNoPathOrData() {
        when(migrationService.run(any(MigrationRunOptions))).thenReturn(successfulRun())

        def summary = bean.run()

        def options = capturedOptions()
        assertEquals(TRIGGER_JMX, options.trigger)
        assertNull(options.path)
        assertNull(options.data)

        assertTrue(summary.contains("test-run-id"))
        assertTrue(summary.contains("SUCCESS"))
    }

    @Test
    void runWithPathPassesPathToOptions() {
        when(migrationService.run(any(MigrationRunOptions))).thenReturn(successfulRun())

        bean.run("/conf/groovyconsole/scripts/migration/sub")

        def options = capturedOptions()
        assertEquals(TRIGGER_JMX, options.trigger)
        assertEquals("/conf/groovyconsole/scripts/migration/sub", options.path)
    }

    @Test
    void runWithPathAndDataPassesBothToOptions() {
        when(migrationService.run(any(MigrationRunOptions))).thenReturn(successfulRun())

        bean.run("/conf/groovyconsole/scripts/migration/sub", '{"foo":"bar"}')

        def options = capturedOptions()
        assertEquals("/conf/groovyconsole/scripts/migration/sub", options.path)
        assertEquals('{"foo":"bar"}', options.data)
    }

    @Test
    void runSummaryIncludesFailedScriptError() {
        when(migrationService.run(any(MigrationRunOptions))).thenReturn(failedRun())

        def summary = bean.run()

        assertTrue(summary.contains("FAILED"))
        assertTrue(summary.contains("script failed"))
    }

    @Test
    void getRunsSummarizesTheGivenNumberOfRuns() {
        when(migrationService.runs).thenReturn([successfulRun(), failedRun()])

        def summary = bean.getRuns(2)

        assertTrue(summary.contains("SUCCESS"))
        assertTrue(summary.contains("FAILED"))
    }

    private MigrationRunOptions capturedOptions() {
        def captor = ArgumentCaptor.forClass(MigrationRunOptions)

        verify(migrationService).run(captor.capture())

        captor.value
    }

    private static DefaultMigrationRun successfulRun() {
        new DefaultMigrationRun(
                runId: "test-run-id",
                status: MigrationStatus.SUCCESS,
                trigger: TRIGGER_JMX,
                runningTime: "00:00:01.000",
                error: "",
                path: "",
                results: [
                        new DefaultMigrationScriptResult(
                                scriptPath: "/conf/groovyconsole/scripts/migration/001-first.groovy",
                                status: MigrationStatus.SUCCESS,
                                runningTime: "00:00:00.500",
                                error: ""
                        )
                ]
        )
    }

    private static DefaultMigrationRun failedRun() {
        new DefaultMigrationRun(
                runId: "test-run-id-failed",
                status: MigrationStatus.FAILED,
                trigger: TRIGGER_JMX,
                runningTime: "00:00:01.000",
                error: "",
                path: "",
                results: [
                        new DefaultMigrationScriptResult(
                                scriptPath: "/conf/groovyconsole/scripts/migration/002-second.groovy",
                                status: MigrationStatus.FAILED,
                                runningTime: "00:00:00.500",
                                error: "java.lang.RuntimeException: script failed\n\tat ..."
                        )
                ]
        )
    }
}
