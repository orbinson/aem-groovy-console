package be.orbinson.aem.groovy.console.migration.impl

import be.orbinson.aem.groovy.console.migration.MigrationRun
import be.orbinson.aem.groovy.console.migration.MigrationScriptResult
import be.orbinson.aem.groovy.console.migration.MigrationStatus
import groovy.transform.ToString
import groovy.transform.TupleConstructor
import org.apache.sling.api.resource.Resource

import static be.orbinson.aem.groovy.console.migration.MigrationConstants.*

@TupleConstructor
@ToString(includePackage = false, includes = ["runId", "status"])
class DefaultMigrationRun implements MigrationRun {

    String runId

    MigrationStatus status

    String trigger

    Calendar startDate

    Calendar endDate

    String runningTime

    String error = ""

    String path = ""

    List<MigrationScriptResult> results = []

    static DefaultMigrationRun fromResource(Resource resource) {
        def properties = resource.valueMap

        def results = (resource.getChild(NN_RESULTS)?.listChildren() ?: [])
                .collect { child -> DefaultMigrationScriptResult.fromResource(child) }
                .sort { result -> result.scriptPath }

        new DefaultMigrationRun(
                runId: resource.name,
                status: MigrationStatus.valueOf(properties.get(PN_STATUS, MigrationStatus.RUNNING.name())),
                trigger: properties.get(PN_TRIGGER, ""),
                startDate: properties.get(PN_START_DATE, Calendar),
                endDate: properties.get(PN_END_DATE, Calendar),
                runningTime: properties.get(PN_RUNNING_TIME, ""),
                error: properties.get(PN_ERROR, ""),
                path: properties.get(PATH, ""),
                results: results
        )
    }
}
