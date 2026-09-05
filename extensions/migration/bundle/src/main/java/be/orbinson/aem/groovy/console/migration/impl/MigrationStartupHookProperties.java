package be.orbinson.aem.groovy.console.migration.impl;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Option;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "Groovy Console Migration Startup Hook",
    description = "Automatically runs pending migration scripts after a deployment.  On AEM as a Cloud Service there "
        + "is no manual step or install hook in the standard pipeline, so this hook enqueues a run once the instance "
        + "is ready.  Cloud instances are detected via the composite node store.")
public @interface MigrationStartupHookProperties {

    @AttributeDefinition(name = "Auto Run On Startup",
        description = "When to automatically enqueue a migration run after the bundle activates.  'cloudOnly' runs "
            + "only on AEM as a Cloud Service (composite node store), avoiding a run on every on-premises restart; "
            + "'always' runs on every startup (run-once semantics still skip unchanged scripts); 'never' disables the "
            + "hook so runs are triggered manually via the HTTP API, JMX or the resource listener.",
        options = {
            @Option(label = "Cloud only (AEMaaCS)", value = "cloudOnly"),
            @Option(label = "Always", value = "always"),
            @Option(label = "Never", value = "never")
        })
    String autoRunOnStartup() default "cloudOnly";

    @AttributeDefinition(name = "Boot Delay",
        description = "Time in milliseconds to wait after bundle activation before attempting the startup run, giving "
            + "the instance time to settle.")
    long bootDelayMillis() default 10000;

    @AttributeDefinition(name = "Readiness Timeout",
        description = "Maximum time in milliseconds to wait for the repository/service user to become available "
            + "before giving up on the startup run.")
    long readinessTimeoutMillis() default 300000;
}
