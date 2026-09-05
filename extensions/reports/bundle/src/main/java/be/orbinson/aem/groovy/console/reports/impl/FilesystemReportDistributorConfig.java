package be.orbinson.aem.groovy.console.reports.impl;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "AEM Groovy Console Reports - Filesystem Distributor",
        description = "Distributes report results to the local filesystem. Disabled by default; writing to disk is "
                + "a sensitive capability, so enable it deliberately and constrain it with an allowed root directory.")
public @interface FilesystemReportDistributorConfig {

    @AttributeDefinition(name = "Enabled",
            description = "Enable the filesystem report distributor.")
    boolean enabled() default false;

    @AttributeDefinition(name = "Allowed root directory",
            description = "Absolute path that all target directories must resolve within. Distribution targets "
                    + "resolving outside this root are rejected. Required when enabled.")
    String allowedRootDirectory() default "";
}
