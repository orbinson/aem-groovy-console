package be.orbinson.aem.groovy.console.reports.impl;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "Groovy Console Reports Configuration Service",
    description = "Report execution and result limits.  Who may view, run, create, edit and delete reports is "
        + "governed entirely by JCR access control on /conf/groovyconsole/reports.")
public @interface ReportsConfigurationProperties {

    @AttributeDefinition(name = "Default Page Size",
        description = "Default result page size in the reports UI when a report does not declare its own.")
    int defaultPageSize() default 50;

    @AttributeDefinition(name = "Maximum Result Rows",
        description = "Maximum number of result rows persisted per execution.  Results exceeding this limit are truncated and flagged.")
    int maxResultRows() default 10000;

    @AttributeDefinition(name = "Maximum Output Length",
        description = "Maximum number of characters of captured script output persisted per execution.")
    int maxOutputLength() default 100000;
}
