package be.orbinson.aem.groovy.console.migration.impl

import be.orbinson.aem.groovy.console.migration.MigrationScriptState
import be.orbinson.aem.groovy.console.migration.MigrationStatus
import groovy.transform.ToString
import groovy.transform.TupleConstructor

@TupleConstructor
@ToString(includePackage = false, includes = ["scriptPath", "status", "pending"])
class DefaultMigrationScriptState implements MigrationScriptState {

    String scriptPath

    String checksum

    MigrationStatus status

    Calendar lastRunDate

    String runningTime

    boolean always

    boolean pending
}
