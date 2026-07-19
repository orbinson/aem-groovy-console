package be.orbinson.aem.groovy.console.migration.healthcheck

import be.orbinson.aem.groovy.console.migration.MigrationRun
import be.orbinson.aem.groovy.console.migration.MigrationService
import be.orbinson.aem.groovy.console.migration.MigrationStatus
import org.apache.felix.hc.api.HealthCheck
import org.apache.felix.hc.api.Result
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * Reports the status of the most recent migration run, mirroring AECU's {@code LastRunHealthCheck}: CRITICAL if
 * the last run failed, WARN if a run is still in progress, OK if the last run succeeded or none has run yet.
 */
@Component(service = HealthCheck, property = [
        "hc.name=AEM Groovy Console Migration - Last Run",
        "hc.tags=migration",
        "hc.tags=migration-last-run",
        "hc.mbean.name=migrationLastRunHC"
])
class MigrationLastRunHealthCheck implements HealthCheck {

    @Reference
    private MigrationService migrationService

    @Override
    Result execute() {
        MigrationRun lastRun = migrationService.runs.find()

        if (!lastRun) {
            return new Result(Result.Status.OK, "no migration runs recorded yet")
        }

        switch (lastRun.status) {
            case MigrationStatus.FAILED:
                return new Result(Result.Status.CRITICAL, "last migration run ${describe(lastRun)} failed")
            case MigrationStatus.RUNNING:
                return new Result(Result.Status.WARN, "migration run ${describe(lastRun)} is currently in progress")
            case MigrationStatus.SUCCESS:
                return new Result(Result.Status.OK, "last migration run ${describe(lastRun)} succeeded")
            default:
                return new Result(Result.Status.WARN,
                        "last migration run ${describe(lastRun)} has unexpected status ${lastRun.status}")
        }
    }

    private static String describe(MigrationRun run) {
        "${run.runId} (trigger ${run.trigger}, started ${run.startDate?.time}, ${run.runningTime})"
    }
}
