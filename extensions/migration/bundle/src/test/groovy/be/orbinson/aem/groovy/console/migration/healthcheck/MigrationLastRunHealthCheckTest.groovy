package be.orbinson.aem.groovy.console.migration.healthcheck

import be.orbinson.aem.groovy.console.migration.MigrationService
import be.orbinson.aem.groovy.console.migration.MigrationStatus
import be.orbinson.aem.groovy.console.migration.impl.DefaultMigrationRun
import io.wcm.testing.mock.aem.junit5.AemContext
import io.wcm.testing.mock.aem.junit5.AemContextExtension
import org.apache.felix.hc.api.Result
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertTrue
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.when

@ExtendWith(AemContextExtension.class)
class MigrationLastRunHealthCheckTest {

    private final AemContext context = new AemContext()

    MigrationLastRunHealthCheck healthCheck

    MigrationService migrationService

    @BeforeEach
    void beforeEach() {
        migrationService = mock(MigrationService)

        context.registerService(MigrationService, migrationService)

        healthCheck = context.registerInjectActivateService(new MigrationLastRunHealthCheck())
    }

    @Test
    void okWhenNoRunsRecordedYet() {
        when(migrationService.runs).thenReturn([])

        def result = healthCheck.execute()

        assertEquals(Result.Status.OK, result.status)
        assertTrue(result.toString().contains("no migration runs recorded yet"))
    }

    @Test
    void okWhenLastRunSucceeded() {
        when(migrationService.runs).thenReturn([run(MigrationStatus.SUCCESS)])

        def result = healthCheck.execute()

        assertEquals(Result.Status.OK, result.status)
        assertTrue(result.toString().contains("succeeded"))
    }

    @Test
    void criticalWhenLastRunFailed() {
        when(migrationService.runs).thenReturn([run(MigrationStatus.FAILED)])

        def result = healthCheck.execute()

        assertEquals(Result.Status.CRITICAL, result.status)
        assertTrue(result.toString().contains("failed"))
    }

    @Test
    void warnWhenLastRunStillInProgress() {
        when(migrationService.runs).thenReturn([run(MigrationStatus.RUNNING)])

        def result = healthCheck.execute()

        assertEquals(Result.Status.WARN, result.status)
        assertTrue(result.toString().contains("in progress"))
    }

    @Test
    void onlyLooksAtTheNewestRun() {
        when(migrationService.runs).thenReturn([run(MigrationStatus.SUCCESS), run(MigrationStatus.FAILED)])

        def result = healthCheck.execute()

        assertEquals(Result.Status.OK, result.status)
    }

    private static DefaultMigrationRun run(MigrationStatus status) {
        new DefaultMigrationRun(
                runId: "test-run-id",
                status: status,
                trigger: "API",
                startDate: Calendar.instance,
                runningTime: "00:00:01.000",
                error: "",
                path: "",
                results: []
        )
    }
}
