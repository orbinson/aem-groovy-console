package be.orbinson.aem.groovy.console.migration.impl

import be.orbinson.aem.groovy.console.migration.MigrationRunOptions
import be.orbinson.aem.groovy.console.migration.MigrationService
import groovy.util.logging.Slf4j
import org.apache.sling.api.resource.observation.ResourceChange
import org.apache.sling.api.resource.observation.ResourceChangeListener
import org.jetbrains.annotations.NotNull
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.osgi.service.metatype.annotations.Designate

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

import static be.orbinson.aem.groovy.console.migration.MigrationConstants.TRIGGER_LISTENER

/**
 * Optionally enqueues a migration run when migration scripts are added or changed, e.g. by a content package
 * installation.  Bursts of change events are debounced into a single asynchronous run.  Disabled by default.
 *
 * <p>Note: when overriding the migration scripts base path, the <code>resource.paths</code> property of this
 * component must be overridden accordingly via OSGi configuration.</p>
 */
@Component(property = [
        "resource.paths=glob:/conf/groovyconsole/scripts/migration/**",
        "resource.change.types=ADDED",
        "resource.change.types=CHANGED"
])
@Designate(ocd = MigrationScriptListenerProperties)
@Slf4j("LOG")
class MigrationScriptListener implements ResourceChangeListener {

    @Reference
    private MigrationService migrationService

    private boolean enabled

    private long debounceMillis

    private ScheduledExecutorService executor

    private ScheduledFuture<?> pendingEnqueue

    @Activate
    synchronized void activate(MigrationScriptListenerProperties properties) {
        enabled = properties.enabled()
        debounceMillis = properties.debounceMillis()

        if (enabled && executor == null) {
            executor = Executors.newSingleThreadScheduledExecutor()
        }
    }

    @Deactivate
    synchronized void deactivate() {
        executor?.shutdownNow()
        executor = null
    }

    @Override
    synchronized void onChange(@NotNull List<ResourceChange> changes) {
        if (enabled && executor != null) {
            LOG.debug("detected {} migration script change(s), scheduling migration run", changes.size())

            // coalesce event bursts into a single deferred migration run
            pendingEnqueue?.cancel(false)

            pendingEnqueue = executor.schedule({ enqueueMigrationRun() } as Runnable, debounceMillis,
                    TimeUnit.MILLISECONDS)
        }
    }

    private void enqueueMigrationRun() {
        try {
            def runId = migrationService.enqueue(new MigrationRunOptions(trigger: TRIGGER_LISTENER))

            LOG.info("enqueued migration run for changed scripts with run ID : {}", runId)
        } catch (IllegalStateException e) {
            LOG.warn("unable to enqueue migration run for changed scripts : {}", e.message)
        } catch (Exception e) {
            LOG.error("error enqueuing migration run for changed scripts", e)
        }
    }
}
