package be.orbinson.aem.groovy.console.migration.impl

import be.orbinson.aem.groovy.console.migration.MigrationScriptResult
import be.orbinson.aem.groovy.console.migration.MigrationStatus
import groovy.transform.ToString
import groovy.transform.TupleConstructor
import org.apache.sling.api.resource.Resource

import static be.orbinson.aem.groovy.console.migration.MigrationConstants.*

@TupleConstructor
@ToString(includePackage = false, includes = ["scriptPath", "status"])
class DefaultMigrationScriptResult implements MigrationScriptResult {

    String scriptPath

    String checksum

    MigrationStatus status

    String runningTime

    long durationMillis

    String output

    String error

    /** Per-query index audit (statement/plan/needsIndex maps); empty unless index usage was measured. */
    List<Map<String, Object>> queryAudit = []

    static DefaultMigrationScriptResult fromResource(Resource resource) {
        def properties = resource.valueMap

        new DefaultMigrationScriptResult(
                scriptPath: properties.get(PN_SCRIPT_PATH, ""),
                checksum: properties.get(PN_CHECKSUM, ""),
                status: MigrationStatus.valueOf(properties.get(PN_STATUS, MigrationStatus.PENDING.name())),
                runningTime: properties.get(PN_RUNNING_TIME, ""),
                durationMillis: properties.get(PN_DURATION_MILLIS, 0L),
                output: properties.get(PN_OUTPUT, ""),
                error: properties.get(PN_ERROR, "")
        )
    }
}
