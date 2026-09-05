package be.orbinson.aem.groovy.console.migration.impl;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "Groovy Console Migration Script Listener")
public @interface MigrationScriptListenerProperties {

    @AttributeDefinition(name = "Enabled?",
        description = "If enabled, a migration run is automatically enqueued when migration scripts are added or changed, e.g. by a content package installation.")
    boolean enabled() default false;

    @AttributeDefinition(name = "Debounce Delay",
        description = "Time in milliseconds to coalesce bursts of script change events (e.g. during a package installation) into a single migration run.")
    long debounceMillis() default 3000;
}
