package be.orbinson.aem.groovy.console.reports.impl;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "Groovy Console Reports Configuration Service",
    description = "Report result display defaults.  Running and authoring reports requires the same console "
        + "permission as running console scripts; JCR access control on /conf/groovyconsole/reports is an "
        + "additional constraint on which reports each user can see and manage.")
public @interface ReportsConfigurationProperties {

    @AttributeDefinition(name = "Default Page Size",
        description = "Default result page size in the reports UI when a report does not declare its own.")
    int defaultPageSize() default 50;

    @AttributeDefinition(name = "Maximum Result Rows",
        description = "Maximum number of result rows persisted per execution.  A run that produces more is "
            + "failed instead of storing an oversized result.  0 means unlimited.")
    int maxResultRows() default 0;

    @AttributeDefinition(name = "Enable Scheduled Reports",
        description = "When disabled, reports cannot run on a schedule: existing schedules are unregistered on "
            + "startup, any scheduled run that still fires is skipped, and the scheduling UI is hidden.")
    boolean schedulingEnabled() default true;

    @AttributeDefinition(name = "Enable Report Distribution",
        description = "When disabled, report results are never distributed: configured distributions are skipped "
            + "for both scheduled and manual runs, and the distribution UI is hidden.")
    boolean distributionEnabled() default true;
}
