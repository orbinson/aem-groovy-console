package be.orbinson.aem.groovy.console.reports.impl;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "Groovy Console Reports Execution Purge Job")
public @interface ReportExecutionPurgeProperties {

    @AttributeDefinition(name = "Enabled?",
        description = "Whether old report executions are purged on a schedule.")
    boolean enabled() default true;

    @AttributeDefinition(name = "Maximum Age (Days)",
        description = "Executions older than this number of days are deleted.  If 0, no age limit is enforced.")
    int maxAgeDays() default 30;

    @AttributeDefinition(name = "Maximum Executions Per Report",
        description = "Only the newest executions up to this count are kept per report.  If 0, no count limit is enforced.")
    int maxCountPerReport() default 50;

    @AttributeDefinition(name = "Scheduler Expression",
        description = "Cron expression for the purge job.")
    String scheduler_expression() default "0 0 2 * * ?";
}
