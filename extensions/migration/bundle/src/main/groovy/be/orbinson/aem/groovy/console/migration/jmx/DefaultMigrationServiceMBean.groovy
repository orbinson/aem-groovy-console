package be.orbinson.aem.groovy.console.migration.jmx

import be.orbinson.aem.groovy.console.migration.MigrationRun
import be.orbinson.aem.groovy.console.migration.MigrationRunOptions
import be.orbinson.aem.groovy.console.migration.MigrationScriptResult
import be.orbinson.aem.groovy.console.migration.MigrationService
import com.adobe.granite.jmx.annotation.AnnotatedStandardMBean
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import javax.management.NotCompliantMBeanException

import static be.orbinson.aem.groovy.console.migration.MigrationConstants.TRIGGER_JMX

@Component(service = MigrationServiceMBean, immediate = true, property = [
        "jmx.objectname=be.orbinson.aem.groovyconsole:type=Migration"
])
class DefaultMigrationServiceMBean extends AnnotatedStandardMBean implements MigrationServiceMBean {

    @Reference
    private MigrationService migrationService

    DefaultMigrationServiceMBean() throws NotCompliantMBeanException {
        super(MigrationServiceMBean)
    }

    @Override
    boolean isRunning() {
        migrationService.running
    }

    @Override
    List<String> getPendingScripts() {
        migrationService.pendingScripts
    }

    @Override
    String run() {
        summarize(migrationService.run(new MigrationRunOptions(trigger: TRIGGER_JMX)))
    }

    @Override
    String run(String path) {
        summarize(migrationService.run(new MigrationRunOptions(trigger: TRIGGER_JMX, path: path)))
    }

    @Override
    String run(String path, String data) {
        summarize(migrationService.run(new MigrationRunOptions(trigger: TRIGGER_JMX, path: path, data: data)))
    }

    @Override
    String getRuns(int count) {
        migrationService.runs.take(count).collect { run -> summarize(run) }.join("\n")
    }

    private static String summarize(MigrationRun run) {
        def summary = new StringBuilder()
                .append("Run ").append(run.runId).append(" : ").append(run.status)
                .append(" (trigger ").append(run.trigger).append(", ").append(run.runningTime).append(")\n")

        if (run.path) {
            summary.append("Path : ").append(run.path).append("\n")
        }

        if (run.error) {
            summary.append("Error : ").append(run.error).append("\n")
        }

        run.results.each { result -> summary.append(summarize(result)) }

        summary.toString()
    }

    private static String summarize(MigrationScriptResult result) {
        def line = "  ${result.scriptPath} : ${result.status} (${result.runningTime})"

        result.error ? "$line\n    ${result.error.readLines().first()}\n" : "$line\n"
    }
}
