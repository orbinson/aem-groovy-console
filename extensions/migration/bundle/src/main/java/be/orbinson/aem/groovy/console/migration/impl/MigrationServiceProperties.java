package be.orbinson.aem.groovy.console.migration.impl;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "Groovy Console Migration Service")
public @interface MigrationServiceProperties {

    @AttributeDefinition(name = "Scripts Base Paths",
        description = "JCR paths containing the deployment migration scripts, searched in order.  Missing paths are "
            + "skipped.  The immutable /apps path ships with the code image (reaching publish and surviving on "
            + "AEMaaCS where /conf is mutable, author-only content); the mutable /conf path suits authored or ad-hoc "
            + "scripts.",
        cardinality = 20)
    String[] scriptsBasePaths() default {
        "/apps/groovyconsole-migration-scripts",
        "/conf/groovyconsole/scripts/migration"
    };

    @AttributeDefinition(name = "Migration Allowed Groups",
        description = "List of group names that are authorized to trigger migration runs.  By default, only the 'admin' user has permission to trigger migrations.",
        cardinality = 20)
    String[] allowedMigrationGroups() default {};

    @AttributeDefinition(name = "Stale Lock Timeout",
        description = "Time in milliseconds after which an in-progress migration run lock is considered stale and may be taken over.")
    long staleLockMillis() default 1800000;

    @AttributeDefinition(name = "Maximum Run History",
        description = "Maximum number of migration runs to keep in the history.  Older runs are pruned.")
    int maxRunHistory() default 50;

    @AttributeDefinition(name = "Maximum Output Characters",
        description = "Maximum number of script output characters to store per migration result.  Full output is available in the regular audit history.")
    int maxOutputChars() default 4096;

    @AttributeDefinition(name = "Run Mode Filter Enabled?",
        description = "If enabled, scripts with an 'author' or 'publish' file name token (e.g. script.author.groovy) only execute on instances with a matching run mode.")
    boolean runModeFilterEnabled() default true;
}
