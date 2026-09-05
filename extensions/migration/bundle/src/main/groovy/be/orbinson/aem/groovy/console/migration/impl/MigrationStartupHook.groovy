package be.orbinson.aem.groovy.console.migration.impl

import be.orbinson.aem.groovy.console.migration.MigrationRunOptions
import be.orbinson.aem.groovy.console.migration.MigrationService
import groovy.util.logging.Slf4j
import org.apache.sling.api.resource.ResourceResolver
import org.apache.sling.api.resource.ResourceResolverFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.osgi.service.metatype.annotations.Designate

import javax.jcr.Session
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

import static be.orbinson.aem.groovy.console.migration.MigrationConstants.TRIGGER_STARTUP

/**
 * Enqueues a migration run after the bundle activates.  With the default {@code cloudOnly} mode it runs only on a
 * composite node store (AEM as a Cloud Service), where each deployment restarts the container; {@code always} runs on
 * every startup, {@code never} disables the hook.
 */
@Component(immediate = true)
@Designate(ocd = MigrationStartupHookProperties)
@Slf4j("LOG")
class MigrationStartupHook {

    static enum AutoRunMode {
        NEVER, CLOUD_ONLY, ALWAYS

        static AutoRunMode from(String value) {
            switch (value?.toLowerCase()) {
                case "always": return ALWAYS
                case "never": return NEVER
                default: return CLOUD_ONLY
            }
        }
    }

    private static final long READINESS_POLL_MILLIS = 2000

    @Reference
    private MigrationService migrationService

    @Reference
    private ResourceResolverFactory resourceResolverFactory

    private AutoRunMode autoRunMode

    private long bootDelayMillis

    private long readinessTimeoutMillis

    private ScheduledExecutorService executor

    @Activate
    void activate(MigrationStartupHookProperties properties) {
        autoRunMode = AutoRunMode.from(properties.autoRunOnStartup())
        bootDelayMillis = properties.bootDelayMillis()
        readinessTimeoutMillis = properties.readinessTimeoutMillis()

        if (autoRunMode == AutoRunMode.NEVER) {
            LOG.debug("migration startup hook disabled")

            return
        }

        executor = Executors.newSingleThreadScheduledExecutor({ Runnable runnable ->
            def thread = new Thread(runnable, "groovyconsole-migration-startup")
            thread.daemon = true
            thread
        } as ThreadFactory)

        LOG.info("scheduling startup migration check (mode : {}) in {} ms", autoRunMode, bootDelayMillis)

        executor.schedule({ triggerStartupRun() } as Runnable, bootDelayMillis, TimeUnit.MILLISECONDS)
    }

    @Deactivate
    void deactivate() {
        executor?.shutdownNow()
        executor = null
    }

    // package-visible so tests can exercise it directly without the scheduling delay
    void triggerStartupRun() {
        try {
            if (!awaitReadiness()) {
                LOG.warn("repository did not become ready within {} ms, skipping startup migration",
                        readinessTimeoutMillis)

                return
            }

            def cloud = isCompositeNodeStore()

            LOG.info("startup migration check : autoRunMode={}, composite node store (AEMaaCS)={}", autoRunMode, cloud)

            if (autoRunMode == AutoRunMode.CLOUD_ONLY && !cloud) {
                LOG.info("skipping startup migration : not a cloud (composite node store) instance")

                return
            }

            if (migrationService.running) {
                LOG.info("skipping startup migration : a run is already in progress")

                return
            }

            def pending = migrationService.pendingScripts

            if (pending.empty) {
                LOG.info("no pending migration scripts on startup")

                return
            }

            def runId = migrationService.enqueue(new MigrationRunOptions(trigger: TRIGGER_STARTUP))

            LOG.info("enqueued startup migration run with ID : {} for {} pending script(s)", runId, pending.size())
        } catch (IllegalStateException e) {
            LOG.warn("unable to enqueue startup migration run : {}", e.message)
        } catch (Exception e) {
            LOG.error("error during startup migration run", e)
        }
    }

    private boolean awaitReadiness() {
        def deadline = System.currentTimeMillis() + readinessTimeoutMillis

        while (true) {
            try {
                if (withResourceResolver { ResourceResolver resolver -> resolver.adaptTo(Session) != null }) {
                    return true
                }
            } catch (Exception e) {
                LOG.debug("repository not ready yet : {}", e.message)
            }

            if (System.currentTimeMillis() >= deadline) {
                return false
            }

            Thread.sleep(READINESS_POLL_MILLIS)
        }
    }

    protected boolean isCompositeNodeStore() {
        withResourceResolver { ResourceResolver resolver -> isCompositeNodeStore(resolver) }
    }

    // Composite node store (AEMaaCS) detection, as in the AC Tool and ACM: /apps is immutable there, so a session
    // that has write permission yet cannot add a node under /apps is on cloud. The permission guard avoids
    // misclassifying a permission-limited session (hasCapability is also false then). See OAK-6563.
    protected boolean isCompositeNodeStore(ResourceResolver resolver) {
        def session = resolver.adaptTo(Session)

        if (session == null) {
            return false
        }

        try {
            def appsNode = session.getNode("/apps")
            def hasPermission = session.hasPermission("/", Session.ACTION_SET_PROPERTY)
            def hasCapability = session.hasCapability("addNode", appsNode, ["nt:folder"] as Object[])

            hasPermission && !hasCapability
        } catch (Exception e) {
            LOG.warn("could not determine composite node store, assuming non-composite : {}", e.message)

            false
        }
    }

    private <T> T withResourceResolver(Closure<T> closure) {
        resourceResolverFactory.getServiceResourceResolver(null).withCloseable(closure)
    }
}
